package org.ruoyi.controller.legal;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.chat.domain.bo.chat.ChatMessageBo;
import org.ruoyi.common.chat.domain.vo.chat.ChatMessageVo;
import org.ruoyi.service.chat.*;
import org.ruoyi.service.legal.DocumentWord;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
@RestController
@RequestMapping("/legal/document")
public class DocumentController {
 private final ChatSessionOwnershipGuard ownership;
 private final IChatMessageService messages;
 private final DocumentWord word;
 @org.springframework.beans.factory.annotation.Autowired private org.ruoyi.service.legal.DocumentExport exporter;
 @org.springframework.beans.factory.annotation.Autowired private org.ruoyi.service.legal.DocTemplateService templates;
 public DocumentController(ChatSessionOwnershipGuard ownership,IChatMessageService messages,DocumentWord word){this.ownership=ownership;this.messages=messages;this.word=word;}
 public record ExportRequest(String title,String content,String format,Long sessionId,String layout){public ExportRequest(String title,String content,String format,Long sessionId){this(title,content,format,sessionId,null);}}
 public record Draft(Long messageId,Long sessionId,String content,String input){}
 public record SaveRequest(Long messageId,String content){}
 public record ComposeRequest(Long sessionId,org.ruoyi.service.legal.TemplateForm.Input form){}
 @PostMapping("/compose")
 @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
 public R<Draft> compose(@RequestBody ComposeRequest r){
  if(r.sessionId()==null||r.form()==null)throw new ServiceException("请先建立文书会话",400);
  Long owner=user(r.sessionId());var form=r.form();
  templates.lockRevision(form.templateId(),null);
  var version=templates.formVersion(form.type(),form.templateId(),form.version());
  var composed=org.ruoyi.service.legal.TemplateForm.compose(version,form);
  var input=new ChatMessageBo();input.setUserId(owner);input.setSessionId(r.sessionId());input.setRole("user");input.setContent(composed.input());
  if(!messages.insertByBo(input))throw new ServiceException("保存填写内容失败，原输入已保留，请重试",409);
  var answer=new ChatMessageBo();answer.setUserId(owner);answer.setSessionId(r.sessionId());answer.setRole("assistant");answer.setContent(composed.content());
  if(!messages.insertByBo(answer))throw new ServiceException("保存文书失败，原输入已保留，请重试",409);
  return R.ok(new Draft(answer.getId(),r.sessionId(),composed.content(),composed.input()));
 }
 private String input(Long sessionId,Long owner){
  if(sessionId==null)return "";var query=new ChatMessageBo();query.setUserId(owner);query.setSessionId(sessionId);var list=messages.queryList(query);
  Long answer=list.stream().filter(m->"assistant".equals(m.getRole())&&m.getId()!=null).map(ChatMessageVo::getId).max(Long::compareTo).orElse(null);
  return list.stream().filter(m->"user".equals(m.getRole())&&m.getId()!=null&&(answer==null||m.getId()<answer)).max(Comparator.comparing(ChatMessageVo::getId)).map(ChatMessageVo::getContent).orElse("");
 }
 private Long user(Long sessionId){
  Long id=LoginHelper.getUserId();if(id==null||id<=0)throw new ServiceException("请先登录",401);
  if(sessionId!=null)try{ownership.requireOwned(id,sessionId);}catch(IllegalArgumentException e){throw new ServiceException("文书不可用",404);}
  return id;
 }
 @PostMapping("/export")
 public ResponseEntity<byte[]> export(@RequestBody ExportRequest r){
  Long owner=user(r.sessionId());
  String request=input(r.sessionId(),owner);
  var file=r.layout()==null||"template".equals(r.layout())?exporter.generate(r.title(),r.content(),r.format(),request):exporter.generate(r.title(),r.content(),r.format(),request,r.layout());
  String name=DocumentWord.filename(r.title()).replaceFirst("\\.docx$","."+file.extension());
  return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.mime()))
   .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(name,StandardCharsets.UTF_8).build().toString())
   .header(HttpHeaders.CACHE_CONTROL,"no-store").body(file.bytes());
 }
 @GetMapping("/draft/{sessionId}")
 public R<Draft> draft(@PathVariable Long sessionId){
  Long owner=user(sessionId);var q=new ChatMessageBo();q.setUserId(owner);q.setSessionId(sessionId);
  var list=messages.queryList(q);
  var a=list.stream().filter(m->"assistant".equals(m.getRole())&&m.getId()!=null).max(Comparator.comparing(ChatMessageVo::getId)).orElseThrow(()->new ServiceException("会话尚无草稿，请生成后再试",404));
  String input=list.stream().filter(m->"user".equals(m.getRole())&&m.getId()!=null&&m.getId()<a.getId()).max(Comparator.comparing(ChatMessageVo::getId)).map(ChatMessageVo::getContent).orElse("");
  String content=exporter==null?a.getContent():exporter.describeDraft(a.getContent(),input);
  return R.ok(new Draft(a.getId(),sessionId,content,input));
 }
 @PutMapping("/draft/{sessionId}")
 public R<Void> save(@PathVariable Long sessionId,@RequestBody SaveRequest r){
  Long owner=user(sessionId);
  String content=DocumentWord.normalize(r.content());
  if(org.ruoyi.service.legal.TemplateDrafting.read(content)!=null)content=exporter.checkedDraft(content,input(sessionId,owner));
  if(r.messageId()==null){var draft=new ChatMessageBo();draft.setUserId(owner);draft.setSessionId(sessionId);draft.setRole("assistant");draft.setContent(content);if(!messages.insertByBo(draft))throw new ServiceException("保存失败，请重试",409);return R.ok();}
  var a=messages.queryById(r.messageId());
  if(a==null||!owner.equals(a.getUserId())||!sessionId.equals(a.getSessionId())||!"assistant".equals(a.getRole()))throw new ServiceException("草稿不可用",404);
  var change=new ChatMessageBo();change.setId(a.getId());change.setUserId(owner);change.setSessionId(sessionId);change.setRole("assistant");change.setContent(content);
  if(!messages.updateByBo(change))throw new ServiceException("保存失败，请重试",409);return R.ok();
 }
}
