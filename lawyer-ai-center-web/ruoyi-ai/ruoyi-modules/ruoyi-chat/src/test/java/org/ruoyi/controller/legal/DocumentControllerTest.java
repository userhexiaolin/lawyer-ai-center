package org.ruoyi.controller.legal;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.chat.domain.vo.chat.ChatMessageVo;
import org.ruoyi.common.chat.domain.bo.chat.ChatMessageBo;
import org.ruoyi.service.chat.*;
import org.ruoyi.service.legal.DocumentWord;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import java.io.ByteArrayInputStream;
import java.util.List;
@Tag("dev")
class DocumentControllerTest {
 @Test void manualDraftCanBeDownloadedWithoutAnExistingModelReply(){
  var guard=mock(ChatSessionOwnershipGuard.class);var messages=mock(IChatMessageService.class);var exporter=mock(org.ruoyi.service.legal.DocumentExport.class);var c=new DocumentController(guard,messages,new DocumentWord());
  org.springframework.test.util.ReflectionTestUtils.setField(c,"exporter",exporter);
  var input=new ChatMessageVo();input.setId(8L);input.setRole("user");input.setContent("本次起草要素");when(messages.queryList(any())).thenReturn(List.of(input));
  when(exporter.generate("律师函","人工完成的正文","txt","本次起草要素")).thenReturn(new org.ruoyi.service.legal.DocumentExport.File(new byte[]{1},"text/plain","txt"));
  try(var login=mockStatic(LoginHelper.class)){login.when(LoginHelper::getUserId).thenReturn(1L);assertEquals(200,c.export(new DocumentController.ExportRequest("律师函","人工完成的正文","txt",2L)).getStatusCode().value());}
 }
 @Test void headingsListsUnicodeAndLargeTextProduceReadableWord()throws Exception{
  String input="# 民事起诉状\n\n## 当事人\n原告：甲测试\n- **请求**返还10000元\n"+String.join("\n",java.util.stream.IntStream.range(0,600).mapToObj(i->"事实"+i+"："+java.util.UUID.randomUUID()).toList());
  byte[] bytes=new DocumentWord().generate("起诉状:/?*",input);
  try(var doc=new XWPFDocument(new ByteArrayInputStream(bytes))){
   var ps=doc.getParagraphs();assertEquals("民事起诉状",ps.get(0).getText());assertEquals("Heading1",ps.get(0).getStyle());
   assertTrue(ps.stream().anyMatch(p->p.getText().contains("• 请求返还10000元")));
   assertTrue(ps.size()>600);
  }
  assertFalse(DocumentWord.filename("诉状:/?*").contains("/"));
  assertThrows(ServiceException.class,()->new DocumentWord().generate("x"," "));
  assertThrows(ServiceException.class,()->DocumentWord.normalize("字".repeat(200001)));
 }
 @Test void manuallyCompletedDraftCanBeSavedAfterModelFailure(){
  var guard=mock(ChatSessionOwnershipGuard.class);var svc=mock(IChatMessageService.class);when(svc.insertByBo(any())).thenReturn(true);var c=new DocumentController(guard,svc,new DocumentWord());
  try(var login=mockStatic(LoginHelper.class)){login.when(LoginHelper::getUserId).thenReturn(1L);c.save(2L,new DocumentController.SaveRequest(null,"# 律师补完的起诉状"));verify(svc).insertByBo(argThat(v->v.getUserId().equals(1L)&&v.getSessionId().equals(2L)&&v.getRole().equals("assistant")&&v.getContent().contains("律师补完")));}
 }
 @Test void exportRequiresLoginAndRejectsForeignSession(){
  var guard=mock(ChatSessionOwnershipGuard.class);var messages=mock(IChatMessageService.class);var c=new DocumentController(guard,messages,new DocumentWord());
  try(var login=mockStatic(LoginHelper.class)){
   assertThrows(ServiceException.class,()->c.export(new DocumentController.ExportRequest("诉状","正文","docx",null)));
   login.when(LoginHelper::getUserId).thenReturn(1L);
   doThrow(new IllegalArgumentException()).when(guard).requireOwned(1L,2L);
   assertThrows(ServiceException.class,()->c.export(new DocumentController.ExportRequest("诉状","正文","docx",2L)));
   verifyNoInteractions(messages);
  }
 }
 @Test void editedAssistantDraftPersistsAndRestoresWithoutChangingUserMessages(){
  var guard=mock(ChatSessionOwnershipGuard.class);var svc=mock(IChatMessageService.class);
  var a=new ChatMessageVo();a.setId(9L);a.setUserId(1L);a.setSessionId(2L);a.setRole("assistant");a.setContent("原稿");
  when(svc.queryById(9L)).thenReturn(a);when(svc.queryList(any())).thenReturn(List.of(a));
  when(svc.updateByBo(any())).thenAnswer(call->{a.setContent(((ChatMessageBo)call.getArgument(0)).getContent());return true;});
  var c=new DocumentController(guard,svc,new DocumentWord());
  try(var login=mockStatic(LoginHelper.class)){
   login.when(LoginHelper::getUserId).thenReturn(1L);
   c.save(2L,new DocumentController.SaveRequest(9L,"# 修改稿\n原告：乙测试"));
   assertTrue(c.draft(2L).getData().content().contains("乙测试"));
   a.setRole("user");assertThrows(ServiceException.class,()->c.save(2L,new DocumentController.SaveRequest(9L,"恶意")));
   a.setRole("assistant");a.setUserId(3L);assertThrows(ServiceException.class,()->c.save(2L,new DocumentController.SaveRequest(9L,"恶意")));
  }
 }
}
