package org.ruoyi.service.legal;
import org.junit.jupiter.api.*;
import org.ruoyi.mapper.legal.LegalDocTemplateMapper;
import org.ruoyi.domain.entity.legal.LegalDocTemplate;
import org.ruoyi.common.satoken.utils.LoginHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@Tag("dev")
class DocTemplateServiceTest {
 @Test void mortgageCannotUsePropertyTemplateButExplicitStandardDraftStillWorks()throws Exception{
  metadata();var mapper=mock(LegalDocTemplateMapper.class);var service=service(mapper);var files=mock(TemplateFiles.class);
  org.springframework.test.util.ReflectionTestUtils.setField(service,"files",files);
  var row=new LegalDocTemplate();row.setId(9L);row.setDocumentType("律师函");row.setContent("物业服务费催款函");row.setStatus("0");
  when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(java.util.List.of(row));
  byte[] docx;try(var document=new org.apache.poi.xwpf.usermodel.XWPFDocument();var out=new java.io.ByteArrayOutputStream()){document.createParagraph().createRun().setText("物业服务费催款函");document.write(out);docx=out.toByteArray();}
  var blocks=java.util.List.of(new TemplateLayout.Block("p0","物业服务费催款函",false),new TemplateLayout.Block("p1","尚欠物业服务费{{金额}}元。",true));
  when(files.current(9L)).thenReturn(new TemplateFiles.Version("version",9L,"合成.docx","docx",docx,docx,blocks));
  String input="---起草要素---\n"+new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(new DraftingFacts.Factors("甲乙","个人一手住房贷款合同。","起草律师函",""));
  try(var login=mockStatic(LoginHelper.class)){login.when(LoginHelper::getUserId).thenReturn(7L);login.when(LoginHelper::getTenantId).thenReturn("tenant-a");
   assertEquals(422,assertThrows(DraftingException.class,()->service.prompt("律师函",9L,input)).getCode());
   assertEquals(422,assertThrows(DraftingException.class,()->service.prompt("律师函",null,input)).getCode());
   var standard=service.prompt("律师函",9L,input,false);assertEquals(input,standard.content());assertNull(standard.templateId());
  }
 }
 @Test void typePreviewDoesNotLoadFilesAndApplyChangesOnlyTypeWithFreshRevision(){
  metadata();var mapper=mock(LegalDocTemplateMapper.class);var service=service(mapper);var files=mock(TemplateFiles.class);
  org.springframework.test.util.ReflectionTestUtils.setField(service,"files",files);
  var row=new LegalDocTemplate();row.setId(9L);row.setTenantId("tenant-a");row.setDocumentType("律师函");row.setContent("正文");row.setStatus("1");
  when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row);when(files.currentVersionId(9L)).thenReturn("version1");
  when(mapper.update(any(LegalDocTemplate.class),any(LambdaQueryWrapper.class))).thenAnswer(call->{
   LegalDocTemplate changes=call.getArgument(0);assertEquals("申请书",changes.getDocumentType());assertNull(changes.getName());assertNull(changes.getContent());assertNull(changes.getStatus());return 1;
  });
  try(var login=mockStatic(LoginHelper.class)){login.when(LoginHelper::getUserId).thenReturn(7L);login.when(LoginHelper::getTenantId).thenReturn("tenant-a");
   var snapshot=service.typeSnapshot(9L);verify(mapper,never()).update(any(LegalDocTemplate.class),any(LambdaQueryWrapper.class));
   service.updateType(9L,new DocTemplateService.TypeInput("申请书",snapshot.revision()));
   verify(mapper).update(any(LegalDocTemplate.class),any(LambdaQueryWrapper.class));verify(files,never()).current(anyLong());verify(files,never()).load(anyString());
   when(files.currentVersionId(9L)).thenReturn("version2");
   assertEquals(409,assertThrows(DraftingException.class,()->service.updateType(9L,new DocTemplateService.TypeInput("申请书",snapshot.revision()))).getCode());
   verify(mapper,times(1)).update(any(LegalDocTemplate.class),any(LambdaQueryWrapper.class));
  }
 }
 @Test void deletionDoesNotParseOrLoadSourceAndStillScopesToTenant(){
  metadata();var mapper=mock(LegalDocTemplateMapper.class);var service=service(mapper);var files=mock(TemplateFiles.class);
  org.springframework.test.util.ReflectionTestUtils.setField(service,"files",files);
  var row=new LegalDocTemplate();row.setId(9L);when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row);
  when(mapper.delete(any(LambdaQueryWrapper.class))).thenAnswer(call->{LambdaQueryWrapper<LegalDocTemplate> query=call.getArgument(0);assertTrue(query.getSqlSegment().contains("tenant_id"));assertTrue(query.getParamNameValuePairs().containsValue("tenant-a"));return 1;});
  try(var login=mockStatic(LoginHelper.class)){login.when(LoginHelper::getUserId).thenReturn(7L);login.when(LoginHelper::getTenantId).thenReturn("tenant-a");service.remove(9L);verify(mapper).delete(any(LambdaQueryWrapper.class));verifyNoInteractions(files);}
 }
 @Test void changingOnlyTypeUpdatesMetadataWithoutReadingOrRebindingSourceFiles(){
  metadata();var mapper=mock(LegalDocTemplateMapper.class);var service=service(mapper);var files=mock(TemplateFiles.class);
  org.springframework.test.util.ReflectionTestUtils.setField(service,"files",files);
  var row=new LegalDocTemplate();row.setId(9L);row.setDocumentType("民事起诉状");row.setContent("本金____元");row.setStatus("0");
  when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row);
  when(mapper.update(any(LegalDocTemplate.class),any(LambdaQueryWrapper.class))).thenAnswer(call->{
   LegalDocTemplate changes=call.getArgument(0);assertEquals("申请书",changes.getDocumentType());assertEquals("新名称",changes.getName());assertNull(changes.getContent());assertNull(changes.getSourceVersion());assertNull(changes.getBlocks());
   LambdaQueryWrapper<LegalDocTemplate> q=call.getArgument(1);assertTrue(q.getSqlSegment().contains("tenant_id"));assertTrue(q.getParamNameValuePairs().containsValue("tenant-a"));return 1;
  });
  try(var login=mockStatic(LoginHelper.class)){login.when(LoginHelper::getUserId).thenReturn(7L);login.when(LoginHelper::getTenantId).thenReturn("tenant-a");
   service.updateMetadata(9L,new DocTemplateService.Metadata(" 新名称 ","申请书",2,"0","备注"));
   verify(mapper).update(any(LegalDocTemplate.class),any(LambdaQueryWrapper.class));verify(files,never()).load(anyString());verify(files,never()).current(anyLong());
  }
 }
 @Test void rawTemplatesCanBeEnabledWithoutBecomingDraftReady(){
  metadata();var mapper=mock(LegalDocTemplateMapper.class);var service=service(mapper);var files=mock(TemplateFiles.class);
  org.springframework.test.util.ReflectionTestUtils.setField(service,"files",files);
  var row=new LegalDocTemplate();row.setId(9L);row.setDocumentType("民事起诉状");row.setContent("");row.setStatus("1");
  when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row);when(mapper.update(any(LegalDocTemplate.class),any(LambdaQueryWrapper.class))).thenReturn(1);
  when(files.currentConfiguration(9L)).thenReturn(new TemplateFiles.Configuration("raw","原件.pdf","pdf","uploaded","",false,java.util.List.of()));
  try(var login=mockStatic(LoginHelper.class)){login.when(LoginHelper::getUserId).thenReturn(7L);login.when(LoginHelper::getTenantId).thenReturn("tenant-a");
   service.updateMetadata(9L,new DocTemplateService.Metadata("原件","民事起诉状",0,"1",""));verifyNoInteractions(files);
   when(files.currentSummaries(java.util.List.of(9L))).thenReturn(java.util.Map.of(9L,new TemplateFiles.SourceSummary("raw","原件.pdf","pdf","uploaded","",false)));
   service.updateMetadata(9L,new DocTemplateService.Metadata("原件","民事起诉状",0,"0",""));
   verify(files).currentSummaries(java.util.List.of(9L));verify(files,never()).load(anyString());verify(files,never()).current(anyLong());verify(mapper,times(2)).update(any(LegalDocTemplate.class),any(LambdaQueryWrapper.class));
  }
 }
 @Test void metadataEditsHonorManualTypeAndPreserveForeignTenantBoundary(){
  metadata();var mapper=mock(LegalDocTemplateMapper.class);var service=service(mapper);var files=mock(TemplateFiles.class);
  org.springframework.test.util.ReflectionTestUtils.setField(service,"files",files);
  var row=new LegalDocTemplate();row.setId(9L);row.setDocumentType("律师函");row.setContent("律师函\n正文");row.setStatus("0");when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row);
  var input=new DocTemplateService.Metadata("模板","民事起诉状",0,"0","");
  try(var login=mockStatic(LoginHelper.class)){login.when(LoginHelper::getUserId).thenReturn(7L);login.when(LoginHelper::getTenantId).thenReturn("tenant-a");
   when(mapper.update(any(LegalDocTemplate.class),any(LambdaQueryWrapper.class))).thenReturn(1);
   service.updateMetadata(9L,input);
   when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(call->{LambdaQueryWrapper<LegalDocTemplate> q=call.getArgument(0);assertTrue(q.getSqlSegment().contains("tenant_id"));assertTrue(q.getParamNameValuePairs().containsValue("tenant-a"));return null;});
   assertEquals(404,assertThrows(DraftingException.class,()->service.updateMetadata(9L,input)).getCode());
   verify(mapper).update(argThat((LegalDocTemplate changed)->"民事起诉状".equals(changed.getDocumentType())&&changed.getContent()==null),any(LambdaQueryWrapper.class));verifyNoInteractions(files);
  }
 }
 @Test void metadataUpdateReportsConflictWhenRowIsNoLongerAvailable(){
  metadata();var mapper=mock(LegalDocTemplateMapper.class);var service=service(mapper);
  var row=new LegalDocTemplate();row.setId(9L);row.setDocumentType("申请书");row.setContent("正文");row.setStatus("1");when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row);
  try(var login=mockStatic(LoginHelper.class)){login.when(LoginHelper::getUserId).thenReturn(7L);login.when(LoginHelper::getTenantId).thenReturn("tenant-a");
   assertEquals(409,assertThrows(DraftingException.class,()->service.updateMetadata(9L,new DocTemplateService.Metadata("模板","申请书",0,"1",""))).getCode());
  }
 }
 @Test void standardDraftingExplicitlySkipsEvenTheConfiguredDefaultTemplate(){
  var mapper=mock(LegalDocTemplateMapper.class);var service=service(mapper);
  var result=service.prompt("民事起诉状",99L,"用户事实",false);
  assertEquals("用户事实",result.content());assertNull(result.templateId());verifyNoInteractions(mapper);
 }
 @Test void inferredTypeIsAdvisoryWhileOriginalFileReadinessStillBlocksDrafting(){
  metadata();var mapper=mock(LegalDocTemplateMapper.class);var service=service(mapper);
  var row=new LegalDocTemplate();row.setId(12L);row.setName("律师函模板");row.setDocumentType("民事起诉状");row.setContent("律 师 函（模板）\n致：收件人");row.setSortOrder(0);
  when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(java.util.List.of(row));
  try(var login=mockStatic(LoginHelper.class)){login.when(LoginHelper::getUserId).thenReturn(7L);login.when(LoginHelper::getTenantId).thenReturn("tenant-a");
   var option=service.enabled("民事起诉状").get(0);assertTrue(option.compatible());assertTrue(option.warning().contains("辅助识别为“律师函”"));
   var failure=assertThrows(DraftingException.class,()->service.prompt("民事起诉状",12L,"真实要素"));assertEquals(422,failure.getCode());assertTrue(failure.getMessage().contains("补传原文件"));
  }
 }
 private DocTemplateService service(LegalDocTemplateMapper mapper){
  var types=mock(DocTemplateTypeService.class);
  when(types.require(anyString(),anyBoolean(),anyBoolean())).thenAnswer(call->{String name=call.getArgument(0);if(DocumentType.options().stream().noneMatch(t->t.value().equals(name))&&!"申请书".equals(name))throw new DraftingException("模板类型不存在",400);return name;});
  return new DocTemplateService(mapper,types);
 }
 @Test void uploadRequiresTenantAndOnlyPreviewsWithoutDatabaseWrites(){
  var mapper=mock(LegalDocTemplateMapper.class);var service=service(mapper);
  var file=new org.springframework.mock.web.MockMultipartFile("file","起诉状.md","text/plain","# {{原告}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  try(var login=mockStatic(LoginHelper.class)){
   assertEquals(401,assertThrows(DraftingException.class,()->service.upload(file,"民事起诉状")).getCode());
   login.when(LoginHelper::getUserId).thenReturn(7L);login.when(LoginHelper::getTenantId).thenReturn("tenant-a");
   var preview=service.upload(file,"民事起诉状");assertEquals("民事起诉状",preview.documentType());assertEquals("# {{原告}}",preview.content());
   assertThrows(DraftingException.class,()->service.upload(file,"未知类型"));
  }
  verifyNoInteractions(mapper);
 }
 @Test void savingImportedTextUsesChosenTypeAndCurrentTenant(){
  var mapper=mock(LegalDocTemplateMapper.class);var service=service(mapper);
  when(mapper.insert(any(LegalDocTemplate.class))).thenAnswer(invocation->{
   LegalDocTemplate row=invocation.getArgument(0);assertEquals("tenant-a",row.getTenantId());assertEquals("律师函",row.getDocumentType());assertEquals("# {{收件人}}",row.getContent());row.setId(19L);return 1;
  });
  try(var login=mockStatic(LoginHelper.class)){
   login.when(LoginHelper::getUserId).thenReturn(7L);login.when(LoginHelper::getTenantId).thenReturn("tenant-a");
   var file=new org.springframework.mock.web.MockMultipartFile("file","律师函.md","text/plain","# {{收件人}}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
   var preview=service.upload(file,"律师函");verifyNoInteractions(mapper);
   assertEquals("19",service.create(new DocTemplateService.Input(preview.name(),preview.documentType(),preview.content(),0,"0","")));
  }
 }
 private void metadata(){com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),"test"),LegalDocTemplate.class);}
 @Test void defaultTemplateUsesServerOrderAndTenantAndPreservesFacts(){
  metadata();var mapper=mock(LegalDocTemplateMapper.class);var service=service(mapper);
  var row=new LegalDocTemplate();row.setId(9L);row.setName("第一模板");row.setContent("# {{原告}}的起诉状\n## 模板专有章节");
  when(mapper.selectList(any(LambdaQueryWrapper.class))).thenAnswer(invocation->{LambdaQueryWrapper<LegalDocTemplate> q=invocation.getArgument(0);String sql=q.getSqlSegment();assertTrue(sql.contains("tenant_id"));assertTrue(sql.contains("sort_order ASC"));assertTrue(sql.contains("id ASC"));assertTrue(q.getParamNameValuePairs().containsValue("tenant-a"));assertTrue(q.getParamNameValuePairs().containsValue("民事起诉状"));return java.util.List.of(row);});
  try(var login=mockStatic(LoginHelper.class)){login.when(LoginHelper::getUserId).thenReturn(7L);login.when(LoginHelper::getTenantId).thenReturn("tenant-a");assertTrue(assertThrows(DraftingException.class,()->service.prompt("民事起诉状",null,"用户事实：本金10000元")).getMessage().contains("补传原文件"));}
 }
 @Test void noEnabledTemplatesFallsBackEvenWhenSelectionBecameStale(){
  var mapper=mock(LegalDocTemplateMapper.class);when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(java.util.List.of());var service=service(mapper);
  try(var login=mockStatic(LoginHelper.class)){login.when(LoginHelper::getUserId).thenReturn(7L);login.when(LoginHelper::getTenantId).thenReturn("tenant-a");var r=service.prompt("民事答辩状",99L,"原始通用提示词");assertNull(r.templateId());assertEquals("原始通用提示词",r.content());}
 }
 @Test void foreignTenantIdCannotReadEditOrDeleteTemplate(){
  metadata();var mapper=mock(LegalDocTemplateMapper.class);var service=service(mapper);
  when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenAnswer(invocation->{LambdaQueryWrapper<LegalDocTemplate> q=invocation.getArgument(0);assertTrue(q.getSqlSegment().contains("tenant_id"));assertTrue(q.getParamNameValuePairs().containsValue("tenant-b"));return null;});
  try(var login=mockStatic(LoginHelper.class)){login.when(LoginHelper::getUserId).thenReturn(8L);login.when(LoginHelper::getTenantId).thenReturn("tenant-b");assertEquals(404,assertThrows(DraftingException.class,()->service.get(9L)).getCode());assertThrows(DraftingException.class,()->service.remove(9L));assertThrows(DraftingException.class,()->service.update(9L,null));}
 }
 @Test void dictionaryRejectsUnknownTypeBeforeQuery(){var mapper=mock(LegalDocTemplateMapper.class);assertThrows(DraftingException.class,()->service(mapper).prompt("手写错误类型",null,"正文"));verifyNoInteractions(mapper);}
 @Test void uploadedCustomTypeCanBeSavedWithoutChangingTheEnum(){
  var mapper=mock(LegalDocTemplateMapper.class);var service=service(mapper);
  when(mapper.insert(any(LegalDocTemplate.class))).thenAnswer(call->{LegalDocTemplate row=call.getArgument(0);assertEquals("申请书",row.getDocumentType());row.setId(20L);return 1;});
  try(var login=mockStatic(LoginHelper.class)){
   login.when(LoginHelper::getUserId).thenReturn(7L);login.when(LoginHelper::getTenantId).thenReturn("tenant-a");
   var preview=service.upload(new org.springframework.mock.web.MockMultipartFile("file","申请书.md","text/plain","# {{申请人}}".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"申请书");
   assertEquals("20",service.create(new DocTemplateService.Input(preview.name(),preview.documentType(),preview.content(),0,"0","")));
  }
 }
 @Test void detailLoadsOnlyPersistedConfigurationWithoutOpeningOriginalOrReadingWordState(){
  metadata();var mapper=mock(LegalDocTemplateMapper.class);var service=service(mapper);var files=mock(TemplateFiles.class);org.springframework.test.util.ReflectionTestUtils.setField(service,"files",files);
  var row=new LegalDocTemplate();row.setId(9L);when(mapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(row);var blocks=java.util.List.of(new TemplateLayout.Block("p0","合成正文",true,java.util.List.of()));
  when(files.currentConfiguration(9L)).thenReturn(new TemplateFiles.Configuration("persisted-version","合成.docx","docx","confirmed","人工已确认",true,blocks));
  try(var login=mockStatic(LoginHelper.class);var word=mockStatic(TemplateLayout.class,call->{throw new AssertionError("Unexpected Word read");})){
   login.when(LoginHelper::getUserId).thenReturn(7L);login.when(LoginHelper::getTenantId).thenReturn("tenant-a");var result=service.get(9L);assertEquals(blocks,result.getBlocks());assertEquals("persisted-version",result.getSourceVersion());assertTrue(result.isFieldsConfirmed());assertEquals(java.util.List.of("p0"),result.getEditableBlocks());verify(files).currentConfiguration(9L);verify(files).currentVersionId(9L);verifyNoMoreInteractions(files);
  }
 }

}
