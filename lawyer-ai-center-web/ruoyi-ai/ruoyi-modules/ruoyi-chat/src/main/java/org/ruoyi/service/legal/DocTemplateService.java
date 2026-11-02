package org.ruoyi.service.legal;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.entity.legal.LegalDocTemplate;
import org.ruoyi.mapper.legal.LegalDocTemplateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import java.util.*;
@Service @RequiredArgsConstructor
public class DocTemplateService {
 private final LegalDocTemplateMapper mapper;
 private final DocTemplateTypeService types;
 @org.springframework.beans.factory.annotation.Autowired private TemplateFiles files;
 public List<DocTemplateTypeService.Option> types(){return types.options(false);}
 public record Metadata(String name,String documentType,Integer sortOrder,String status,String remark){}
 public record TypeInput(String documentType,String expectedRevision){}
 public record TypeSnapshot(Long id,String documentType,String content,String sourceVersion,String revision){}
 public record Input(String name,String documentType,String content,Integer sortOrder,String status,String remark,String sourceVersion,List<String> editableBlocks,boolean layoutConfirmed,List<TemplateFields.Field> fields,boolean fieldsConfirmed,boolean saveDraft,String expectedRevision){
  public Input(String name,String documentType,String content,Integer sortOrder,String status,String remark,String sourceVersion,List<String> editableBlocks,boolean layoutConfirmed,List<TemplateFields.Field> fields,boolean fieldsConfirmed,boolean saveDraft){this(name,documentType,content,sortOrder,status,remark,sourceVersion,editableBlocks,layoutConfirmed,fields,fieldsConfirmed,saveDraft,null);}
  public Input(String name,String documentType,String content,Integer sortOrder,String status,String remark,String sourceVersion,List<String> editableBlocks,boolean layoutConfirmed,List<TemplateFields.Field> fields,boolean fieldsConfirmed){this(name,documentType,content,sortOrder,status,remark,sourceVersion,editableBlocks,layoutConfirmed,fields,fieldsConfirmed,false);}
  public Input(String name,String documentType,String content,Integer sortOrder,String status,String remark){this(name,documentType,content,sortOrder,status,remark,null,null,false,null,false);}
  public Input(String name,String documentType,String content,Integer sortOrder,String status,String remark,String sourceVersion,List<String> editableBlocks,boolean layoutConfirmed){this(name,documentType,content,sortOrder,status,remark,sourceVersion,editableBlocks,layoutConfirmed,null,false);}
 }
 public record Option(String id,String name,String documentType,Integer sortOrder,boolean compatible,String warning,boolean preservesLayout){public Option(String id,String name,String documentType,Integer sortOrder,boolean compatible,String warning){this(id,name,documentType,sortOrder,compatible,warning,false);}}
 public record Listing(List<LegalDocTemplate> rows,long total,String processing){public Listing(List<LegalDocTemplate> rows,long total){this(rows,total,"");}}
 public record Prompt(String content,String templateId,String templateName){}
 /** User-facing schema: same tenant/type/activation gate as drafting, without admin permissions. */
 public TemplateFiles.Version formVersion(String type,Long id,String expectedVersion){
  types.require(type,true,false);var row=stored(id);
  if(!"0".equals(row.getStatus())||!type.equals(row.getDocumentType()))throw new DraftingException("所选模板已停用或不属于当前文书类型，请刷新模板",409);
  var version=files.current(id);
  if(version==null||!TemplateFiles.usable(version))throw new DraftingException("模板填写字段尚未核对，请在模板管理中确认字段",422);
  if(expectedVersion!=null&&!expectedVersion.equals(version.id()))throw new DraftingException("模板版本已变化，请重新加载模板；已填写内容已保留",409);
  return version;
 }
 public TemplateForm.Schema form(String type,Long id){var version=formVersion(type,id,null);return TemplateForm.schema(stored(id).getName(),version);}
 public DocTemplateFile.Preview upload(org.springframework.web.multipart.MultipartFile file,String type){tenant();return DocTemplateFile.read(file,types.require(type,true,false));}
 public TemplateFiles.Upload uploadSource(org.springframework.web.multipart.MultipartFile file,String type){tenant();return files.stage(file,types.require(type,true,false));}
 public TemplateFiles.Upload uploadSource(org.springframework.web.multipart.MultipartFile file,String type,boolean parse){tenant();return files.stage(file,types.require(type,true,false),parse);}
 public TemplateFiles.Upload parse(String version,String type){tenant();return files.parse(version,types.require(type,true,false));}
 public byte[] preview(String version){tenant();return files.preview(version);}
 public TemplateFiles.Upload reidentify(Long id){var row=get(id);return files.reidentify(id,row.getDocumentType());}
 private LegalDocTemplate source(LegalDocTemplate row){
  if(files!=null){var config=files.currentConfiguration(row.getId());if(config!=null){
   row.setSourceVersion(config.version());row.setSourceFormat(config.format());row.setBlocks(config.blocks());row.setSourceFilename(config.filename());row.setParseStatus(config.parseStatus());row.setAnalysisWarning(config.warning());row.setFieldsConfirmed(config.fieldsConfirmed());row.setEditableBlocks(config.blocks().stream().filter(TemplateLayout.Block::editable).map(TemplateLayout.Block::id).toList());
  }}return row;
 }
 private String tenant(){String t=LoginHelper.getTenantId();if(LoginHelper.getUserId()==null||t==null||t.isBlank())throw new DraftingException("请先登录",401);return t;}
 private LambdaQueryWrapper<LegalDocTemplate> scope(){return new LambdaQueryWrapper<LegalDocTemplate>().eq(LegalDocTemplate::getTenantId,tenant());}
 public Listing list(String type,String name,int page,int size){
  return list(type,name,page,size,null);
 }
 public Listing list(String type,String name,int page,int size,String processing){
  var q=scope();if(type!=null&&!type.isBlank())q.eq(LegalDocTemplate::getDocumentType,types.require(type,false,false));
  if(name!=null&&!name.isBlank())q.like(LegalDocTemplate::getName,name.trim());
  if(processing!=null&&!processing.isBlank())q.apply(TemplateProcessingFilter.expression(processing),processing,tenant());
  q.orderByDesc(LegalDocTemplate::getSortOrder).orderByAsc(LegalDocTemplate::getId);
  var result=mapper.selectPage(new Page<>(Math.max(1,page),Math.max(1,Math.min(size,100))),q);
  var rows=result.getRecords();
  if(files!=null&&!rows.isEmpty()){
   var summaries=files.currentSummaries(rows.stream().map(LegalDocTemplate::getId).toList());
   for(var row:rows){var summary=summaries.get(row.getId());if(summary!=null){row.setSourceVersion(summary.version());row.setSourceFilename(summary.filename());row.setSourceFormat(summary.format());row.setParseStatus(summary.parseStatus());row.setAnalysisWarning(summary.warning());row.setFieldsConfirmed(summary.fieldsConfirmed());}}
  }
  return new Listing(rows,result.getTotal(),processing==null?"":processing);
 }
 private LegalDocTemplate stored(Long id){return stored(id,false);}
 private LegalDocTemplate stored(Long id,boolean lock){if(id==null)throw new DraftingException("模板不存在或不可访问",404);var query=scope().eq(LegalDocTemplate::getId,id);if(lock)query.last("FOR UPDATE");var row=mapper.selectOne(query);if(row==null)throw new DraftingException("模板不存在或不可访问",404);return row;}
 public TypeSnapshot typeSnapshot(Long id){return typeSnapshot(stored(id));}
 private TypeSnapshot typeSnapshot(LegalDocTemplate row){
  String version=files==null?null:files.currentVersionId(row.getId());
  String value=String.join("\n",Objects.toString(row.getId(),""),Objects.toString(row.getTenantId(),""),Objects.toString(row.getDocumentType(),""),Objects.toString(row.getStatus(),""),Objects.toString(row.getContent(),""),Objects.toString(version,""),row.getUpdateTime()==null?"":Long.toString(row.getUpdateTime().getTime()));
  try{return new TypeSnapshot(row.getId(),row.getDocumentType(),row.getContent(),version,java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))));}
  catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
 public void updateType(Long id,TypeInput input){
  if(input==null||input.expectedRevision()==null||input.expectedRevision().isBlank())throw new DraftingException("模板版本信息缺失，请刷新后保存",400);
  String type=types.require(input.documentType(),true,true);
  var row=stored(id,true);var snapshot=typeSnapshot(row);
  if(!snapshot.revision().equals(input.expectedRevision()))throw new DraftingException("模板或原文件版本已变化，请刷新后重新保存",409);
  // Manual classification is authoritative when saving a valid document type.
  if(type.equals(row.getDocumentType()))return;
  var changes=new LegalDocTemplate();changes.setDocumentType(type);
  if(mapper.update(changes,scope().eq(LegalDocTemplate::getId,id))!=1)throw new DraftingException("模板已变化，请刷新后重试",409);
 }
 public LegalDocTemplate get(Long id){var row=source(stored(id));row.setExpectedRevision(typeSnapshot(row).revision());return row;}
 public void lockRevision(Long id,String expected){var row=stored(id,true);if(expected!=null&&!expected.equals(typeSnapshot(row).revision()))throw new DraftingException("模板版本已变化，请刷新后重试，原字段未被覆盖",409);}
 public List<Option> enabled(String type){
  var rows=mapper.selectList(scope().eq(LegalDocTemplate::getDocumentType,types.require(type,true,false)).eq(LegalDocTemplate::getStatus,"0")
   .select(LegalDocTemplate::getId,LegalDocTemplate::getName,LegalDocTemplate::getDocumentType,LegalDocTemplate::getSortOrder,LegalDocTemplate::getContent).orderByAsc(LegalDocTemplate::getSortOrder,LegalDocTemplate::getId));
  var summaries=files==null||rows.isEmpty()?Map.<Long,TemplateFiles.SourceSummary>of():files.currentSummaries(rows.stream().map(LegalDocTemplate::getId).toList());
  return rows.stream().filter(t->{var source=summaries.get(t.getId());return source==null||source.fieldsConfirmed();}).map(t->{String warning=TemplateTypeCompatibility.advice(type,t.getName(),t.getContent());var source=summaries.get(t.getId());return new Option(t.getId().toString(),t.getName(),t.getDocumentType(),t.getSortOrder(),true,warning,source!=null&&source.fieldsConfirmed());}).toList();
 }
 private List<LegalDocTemplate> draftReady(List<LegalDocTemplate> rows){
  if(files==null||rows.isEmpty())return rows;var summaries=files.currentSummaries(rows.stream().map(LegalDocTemplate::getId).toList());
  return rows.stream().filter(t->{var summary=summaries.get(t.getId());return summary==null||summary.fieldsConfirmed();}).toList();
 }
 private void validateMetadata(Metadata input){
  if(input==null||input.name()==null||input.name().isBlank()||input.name().length()>100)throw new DraftingException("模板名称不能为空且不超过100字",400);
  if(input.sortOrder()==null||input.sortOrder()<0||input.sortOrder()>999999)throw new DraftingException("排序号须为0至999999的整数",400);
  if(!Set.of("0","1").contains(Objects.toString(input.status(),"")))throw new DraftingException("请选择启用或停用状态",400);
  if(input.remark()!=null&&input.remark().length()>500)throw new DraftingException("备注不超过500字",400);
 }
 private void copy(LegalDocTemplate row,Input input){
  validateMetadata(input==null?null:new Metadata(input.name(),input.documentType(),input.sortOrder(),input.status(),input.remark()));
  if(input.content()==null||input.content().length()>40000||(input.content().isBlank()&&!(input.sourceVersion()!=null&&files!=null&&"uploaded".equals(TemplateFiles.parseStatus(files.load(input.sourceVersion())))&&input.saveDraft())))throw new DraftingException("模板正文不能为空且不超过40000字",400);
  if(!input.saveDraft()&&"0".equals(input.status())&&input.sourceVersion()!=null){var v=files.load(input.sourceVersion());if(!TemplateFiles.usable(v)&&(!input.fieldsConfirmed()||"uploaded".equals(TemplateFiles.parseStatus(v))))throw new DraftingException("请先解析并核对填写字段后启用模板",400);}
  boolean existing=row.getId()!=null&&Objects.equals(row.getDocumentType(),input.documentType())&&!("1".equals(row.getStatus())&&"0".equals(input.status()));
  String type=types.require(input.documentType(),!existing,true);
  row.setName(input.name().trim());row.setDocumentType(type);row.setContent(input.content());row.setSortOrder(input.sortOrder());row.setStatus(input.status());row.setRemark(input.remark());
 }
 @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
 public String create(Input input){var row=new LegalDocTemplate();row.setTenantId(tenant());copy(row,input);mapper.insert(row);if(input.sourceVersion()!=null)files.bind(input.sourceVersion(),row.getId(),input.editableBlocks(),input.content(),input.layoutConfirmed(),input.fields(),input.fieldsConfirmed(),input.saveDraft());return row.getId().toString();}
 /** Basic edits change activation intent; drafting independently enforces reviewed source readiness. */
 @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
 public void updateMetadata(Long id,Metadata input){
  var row=stored(id);validateMetadata(input);
  boolean enabling="1".equals(row.getStatus())&&"0".equals(input.status());
  String type=types.require(input.documentType(),enabling||!Objects.equals(row.getDocumentType(),input.documentType()),true);
  if("0".equals(input.status())){
   if(row.getContent()==null||row.getContent().isBlank()){
    var summary=files==null?null:files.currentSummaries(List.of(id)).get(id);
    if(summary==null||!"uploaded".equals(summary.parseStatus()))throw new DraftingException("模板正文不能为空且不超过40000字",400);
   }
  }
  var changes=new LegalDocTemplate();changes.setName(input.name().trim());changes.setDocumentType(type);changes.setSortOrder(input.sortOrder());changes.setStatus(input.status());changes.setRemark(input.remark());
  if(mapper.update(changes,scope().eq(LegalDocTemplate::getId,id).eq(LegalDocTemplate::getStatus,row.getStatus()).eq(LegalDocTemplate::getDocumentType,row.getDocumentType()))!=1)throw new DraftingException("模板已变化，请刷新后重试",409);
 }
 @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
 public void update(Long id,Input input){if(input!=null&&input.expectedRevision()!=null)lockRevision(id,input.expectedRevision());var row=get(id);if(input!=null&&"0".equals(input.status())&&row.getSourceVersion()!=null&&input.sourceVersion()==null&&!row.isFieldsConfirmed())throw new DraftingException("请先解析并核对填写字段后启用模板",400);if(row.getSourceVersion()!=null&&(input.sourceVersion()==null||input.sourceVersion().equals(row.getSourceVersion()))&&!row.getContent().equals(input.content()))throw new DraftingException("保留原格式的模板请通过替换文件修改正文",400);copy(row,input);if(mapper.update(row,scope().eq(LegalDocTemplate::getId,id))!=1)throw new DraftingException("模板已变化，请刷新后重试",409);if(input.sourceVersion()!=null){files.bind(input.sourceVersion(),id,input.editableBlocks(),input.content(),input.layoutConfirmed(),input.fields(),input.fieldsConfirmed(),input.saveDraft());if(input.fieldsConfirmed())files.markAnalysisReviewed(id,input.expectedRevision());}}
 public void remove(Long id){stored(id);if(mapper.delete(scope().eq(LegalDocTemplate::getId,id))!=1)throw new DraftingException("模板已变化，请刷新后重试",409);}
 /** Always resolve on the server so missing client selections cannot bypass defaults. */
 public Prompt prompt(String type,Long id,String content){
  return prompt(type,id,content,true);
 }
 public Prompt prompt(String type,Long id,String content,boolean useTemplate){
  types.require(type,true,false);if(content==null||content.isBlank()||content.length()>60000)throw new DraftingException("起草要素不能为空且不超过60000字",400);
  var input=LitigationDrafting.input(content);
  if(input!=null&&!type.equals(input.type()))throw new DraftingException("起草要素与所选文书类型不一致，请刷新后重试",400);
  if(!useTemplate)return new Prompt(content,null,null);
  var q=scope().eq(LegalDocTemplate::getDocumentType,type).eq(LegalDocTemplate::getStatus,"0");
  if(id!=null)q.eq(LegalDocTemplate::getId,id);
  var rows=mapper.selectList(q.orderByAsc(LegalDocTemplate::getSortOrder,LegalDocTemplate::getId).last(id==null?"":"LIMIT 1"));
  if(id==null)rows=draftReady(rows).stream().limit(1).toList();
  if(rows.isEmpty()){
   // If every template was disabled after the selector loaded, generic generation
   // must still work. An obsolete selection cannot block the no-template path.
   if(id!=null&&!enabled(type).isEmpty())throw new DraftingException("所选模板已停用、删除或不属于当前文书类型，请刷新模板后重试",409);
   return new Prompt(content,null,null);
  }
  var t=rows.get(0);
  if(files!=null){var version=files.current(t.getId());if(version!=null){if(!TemplateFiles.usable(version))throw new DraftingException("模板填写字段尚未核对，请先完成字段配置",422);TemplateDrafting.requireCompatible(content,version.blocks());return new Prompt(content+TemplateDrafting.prompt(version),t.getId().toString(),t.getName());}}
  throw new DraftingException("该历史模板仅保存正文；严格按原模板填充需在模板管理中补传原文件后再使用",422);
 }
}
