package org.ruoyi.controller.legal;
import cn.dev33.satoken.annotation.*;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.domain.entity.legal.LegalDocTemplate;
import org.ruoyi.service.legal.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequiredArgsConstructor @SaCheckLogin
@RequestMapping("/legal/doc-template")
public class DocTemplateController {
 private final DocTemplateService service;
 private final TemplateFieldAnalysis fieldAnalysis;
 @org.springframework.beans.factory.annotation.Autowired private TemplateFieldTask fieldTasks;
 public record FieldTaskRequest(boolean replacePending){}
 @SaCheckPermission("legal:doc-template:edit") @PostMapping("/{id}/field-analysis")
 public R<TemplateFieldTask.Status> analyzeFields(@PathVariable("id") Long id,@RequestBody(required=false) FieldTaskRequest request){return R.ok(fieldTasks.start(id,request!=null&&request.replacePending()));}
 @SaCheckPermission(value={"legal:doc-template:query","legal:doc-template:edit"},mode=SaMode.AND) @GetMapping("/{id}/field-analysis/result")
 public R<LegalDocTemplate> fieldResult(@PathVariable("id") Long id){return R.ok(fieldTasks.result(id));}
 @SaCheckPermission("legal:doc-template:list") @GetMapping("/{id}/field-analysis")
 public R<TemplateFieldTask.Status> fieldStatus(@PathVariable("id") Long id){return R.ok(fieldTasks.status(id));}
 @SaCheckPermission(value={"legal:doc-template:add","legal:doc-template:edit"},mode=SaMode.OR)
 @PostMapping("/fields/{version}") public R<TemplateFieldAnalysis.Analysis> fields(@PathVariable("version") String version){return R.ok(fieldAnalysis.analyze(version));}
 @SaCheckPermission("legal:doc-template:edit")
 @PostMapping("/{id}/reidentify") public R<TemplateFiles.Upload> reidentify(@PathVariable("id") Long id){return R.ok(service.reidentify(id));}
 @GetMapping("/types") public R<List<DocTemplateTypeService.Option>> types(){return R.ok(service.types());}
 @SaCheckPermission(value={"legal:doc-template:add","legal:doc-template:edit"},mode=SaMode.OR)
 @PostMapping(value="/upload",consumes="multipart/form-data")
 public R<TemplateFiles.Upload> upload(@RequestPart("file") org.springframework.web.multipart.MultipartFile file,@RequestParam("type") String type,@RequestParam(value="parse",defaultValue="true") boolean parse){return R.ok(service.uploadSource(file,type,parse));}
 public R<TemplateFiles.Upload> upload(org.springframework.web.multipart.MultipartFile file,String type){return R.ok(service.uploadSource(file,type));}
 @SaCheckPermission("legal:doc-template:edit")
 @PostMapping("/parse/{version}") public R<TemplateFiles.Upload> parse(@PathVariable("version") String version,@RequestParam("type") String type){return R.ok(service.parse(version,type));}
 @SaCheckPermission(value={"legal:doc-template:add","legal:doc-template:edit"},mode=SaMode.OR)
 @GetMapping("/preview/{version}") public org.springframework.http.ResponseEntity<byte[]> preview(@PathVariable("version") String version){return org.springframework.http.ResponseEntity.ok().contentType(org.springframework.http.MediaType.APPLICATION_PDF).header("Cache-Control","no-store").body(service.preview(version));}
 @GetMapping("/enabled") public R<List<DocTemplateService.Option>> enabled(@RequestParam("type") String type){return R.ok(service.enabled(type));}
 public record PromptRequest(String type,Long templateId,String content,Boolean useTemplate){}
 @PostMapping("/prompt") public R<DocTemplateService.Prompt> prompt(@RequestBody PromptRequest r){return R.ok(service.prompt(r.type(),r.templateId(),r.content(),!Boolean.FALSE.equals(r.useTemplate())));}
 @GetMapping("/form") public R<org.ruoyi.service.legal.TemplateForm.Schema> form(@RequestParam String type,@RequestParam Long templateId){return R.ok(service.form(type,templateId));}
 @SaCheckPermission("legal:doc-template:list") @GetMapping("/list")
 public R<DocTemplateService.Listing> list(@RequestParam(value="type",required=false) String type,@RequestParam(value="name",required=false) String name,@RequestParam(value="pageNum",defaultValue="1") int pageNum,@RequestParam(value="pageSize",defaultValue="20") int pageSize,@RequestParam(value="processing",required=false) String processing){var result=processing==null||processing.isBlank()?service.list(type,name,pageNum,pageSize):service.list(type,name,pageNum,pageSize,processing);if(fieldTasks!=null)fieldTasks.decorate(result.rows());return R.ok(result);}
 public R<DocTemplateService.Listing> list(String type,String name,int pageNum,int pageSize){return list(type,name,pageNum,pageSize,null);}
 @SaCheckPermission("legal:doc-template:query") @GetMapping("/{id}") public R<LegalDocTemplate> get(@PathVariable("id") Long id){return R.ok(service.get(id));}
 @SaCheckPermission("legal:doc-template:add") @PostMapping public R<String> create(@RequestBody DocTemplateService.Input r){return R.ok("操作成功",service.create(r));}
 @SaCheckPermission("legal:doc-template:edit") @PutMapping("/{id}") public R<Void> update(@PathVariable("id") Long id,@RequestBody DocTemplateService.Input r){service.update(id,r);return R.ok();}
 @SaCheckPermission("legal:doc-template:edit") @PutMapping("/{id}/metadata") public R<Void> updateMetadata(@PathVariable("id") Long id,@RequestBody DocTemplateService.Metadata r){service.updateMetadata(id,r);return R.ok();}
 @SaCheckPermission("legal:doc-template:edit") @PutMapping("/{id}/type") public R<Void> updateType(@PathVariable("id") Long id,@RequestBody DocTemplateService.TypeInput r){service.updateType(id,r);return R.ok();}
 public record TypeVersion(String documentType,String revision){}
 @SaCheckPermission("legal:doc-template:edit") @GetMapping("/{id}/type") public R<TypeVersion> typeVersion(@PathVariable("id") Long id){var snapshot=service.typeSnapshot(id);return R.ok(new TypeVersion(snapshot.documentType(),snapshot.revision()));}
 @SaCheckPermission("legal:doc-template:remove") @DeleteMapping("/{id}") public R<Void> remove(@PathVariable("id") Long id){service.remove(id);return R.ok();}
}
