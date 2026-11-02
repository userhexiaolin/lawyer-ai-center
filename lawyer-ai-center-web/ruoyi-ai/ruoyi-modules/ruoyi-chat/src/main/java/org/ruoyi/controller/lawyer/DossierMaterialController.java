package org.ruoyi.controller.lawyer;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.service.lawyer.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController @SaCheckLogin @RequiredArgsConstructor
@RequestMapping("/lawyer/case/{caseId}/dossier")
public class DossierMaterialController {
    private final DossierMaterialService service;
    @ExceptionHandler(ServiceException.class) public ResponseEntity<R<Void>> error(ServiceException e){int code=e.getCode()==null?500:e.getCode();if(code<400||code>599)code=500;return ResponseEntity.status(code).body(R.fail(code,e.getMessage()));}
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class) public ResponseEntity<R<Void>> invalid(){return ResponseEntity.badRequest().body(R.fail(400,"请求字段无效，请检查文件大小、指纹和页码"));}
    public record Init(@NotBlank String fileName,@Min(1) long fileBytes,@NotBlank String sha256,@NotBlank String idempotencyKey,Long materialId){}
    public record Complete(boolean keepDuplicate){}
    public record Parse(@NotNull Long fileVersionId,@NotBlank String idempotencyKey,List<Integer> pages){}
    public record Adopt(@NotBlank String idempotencyKey){}
    public record Blank(@Min(1)int expectedVersion){}
    @GetMapping("/overview") public R<?> overview(@PathVariable Long caseId){return R.ok(service.overview(caseId));}
    @PostMapping("/materials/init-upload") public R<?> init(@PathVariable Long caseId,@Valid @RequestBody Init input){return R.ok(service.init(caseId,new DossierMaterialService.Init(input.fileName(),input.fileBytes(),input.sha256(),input.idempotencyKey(),input.materialId())));}
    @PostMapping(value="/uploads/{uploadId}/content",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public R<?> upload(@PathVariable Long caseId,@PathVariable Long uploadId,@RequestPart("file")MultipartFile file){return R.ok(service.upload(caseId,uploadId,file));}
    @PostMapping("/uploads/{uploadId}/complete") public R<?> complete(@PathVariable Long caseId,@PathVariable Long uploadId,@RequestBody Complete input){return R.ok(service.complete(caseId,uploadId,input.keepDuplicate()));}
    @GetMapping("/materials") public R<?> materials(@PathVariable Long caseId,@RequestParam(defaultValue="")String search,@RequestParam(defaultValue="")String status,@RequestParam(defaultValue="1")int pageNum,@RequestParam(defaultValue="20")int pageSize){return R.ok(service.list(caseId,search,status,pageNum,pageSize));}
    @GetMapping("/materials/{materialId}/versions") public R<?> versions(@PathVariable Long caseId,@PathVariable Long materialId){return R.ok(service.versions(caseId,materialId));}
    @PostMapping("/materials/{materialId}/adopt") public R<?> adopt(@PathVariable Long caseId,@PathVariable Long materialId,@Valid @RequestBody Adopt input){return R.ok(service.adopt(caseId,materialId,input.idempotencyKey()));}
    @GetMapping("/files/{versionId}/pages") public R<?> pages(@PathVariable Long caseId,@PathVariable Long versionId){return R.ok(service.pages(caseId,versionId));}
    @GetMapping("/files/{versionId}/logs") public R<?> logs(@PathVariable Long caseId,@PathVariable Long versionId){return R.ok(service.logs(caseId,versionId));}
    @PostMapping("/files/{versionId}/pages/{page}/confirm-blank") public R<?> blank(@PathVariable Long caseId,@PathVariable Long versionId,@PathVariable int page,@Valid @RequestBody Blank input){service.confirmBlank(caseId,versionId,page,input.expectedVersion());return R.ok();}
    @GetMapping("/files/{versionId}/preview") public ResponseEntity<byte[]> preview(@PathVariable Long caseId,@PathVariable Long versionId,@RequestParam int page){return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(service.preview(caseId,versionId,page));}
    @GetMapping("/files/{versionId}/download") public void download(@PathVariable Long caseId,@PathVariable Long versionId,jakarta.servlet.http.HttpServletResponse response)throws java.io.IOException{service.download(caseId,versionId,response);}
    @PostMapping("/jobs/parse") public R<?> parse(@PathVariable Long caseId,@Valid @RequestBody Parse input){return R.ok(service.retry(caseId,input.fileVersionId(),input.idempotencyKey(),input.pages()));}
    @GetMapping("/jobs") public R<?> jobs(@PathVariable Long caseId){return R.ok(service.jobs(caseId));}
    @GetMapping("/jobs/{jobId}") public R<?> job(@PathVariable Long caseId,@PathVariable Long jobId){return R.ok(service.job(caseId,jobId));}
    @PostMapping("/jobs/{jobId}/cancel") public R<?> cancel(@PathVariable Long caseId,@PathVariable Long jobId){service.cancel(caseId,jobId);return R.ok();}
}
