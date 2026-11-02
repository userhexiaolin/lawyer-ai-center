package org.ruoyi.controller.lawyer;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.service.lawyer.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @SaCheckLogin @RequiredArgsConstructor
@RequestMapping("/lawyer/case/{caseId}/dossier/bank-rows")
public class BankStatementController {
    private final BankStatementService service;private final BankStatementExport exports;
    @ExceptionHandler(ServiceException.class) public ResponseEntity<R<Void>> error(ServiceException e){int code=e.getCode()==null?500:e.getCode();if(code<400||code>599)code=500;return ResponseEntity.status(code).body(R.fail(code,e.getMessage()));}
    @GetMapping("/files") public R<?> files(@PathVariable Long caseId){return R.ok(service.files(caseId));}
    public record Category(String category){} public record Extract(Long fileVersionId,String idempotencyKey){} public record Pending(List<Long> ids,String reason){}
    @PostMapping("/files/{materialId}/category") public R<?> category(@PathVariable Long caseId,@PathVariable Long materialId,@RequestBody Category value){return R.ok(service.classify(caseId,materialId,value.category()));}
    @PostMapping("/extract") public R<?> extract(@PathVariable Long caseId,@RequestBody Extract value){return R.ok(service.extract(caseId,value.fileVersionId(),value.idempotencyKey()));}
    @GetMapping public R<?> list(@PathVariable Long caseId,@ModelAttribute BankStatementService.Filter filter){return R.ok(service.list(caseId,filter));}
    @GetMapping("/{id}") public R<?> detail(@PathVariable Long caseId,@PathVariable Long id){return R.ok(service.detail(caseId,id));}
    @GetMapping("/unparsed") public R<?> unparsed(@PathVariable Long caseId){return R.ok(service.unread(caseId));}
    @PostMapping("/manual") public R<?> manual(@PathVariable Long caseId,@RequestBody BankStatementService.Manual value){return R.ok(service.manual(caseId,value));}
    @PatchMapping("/{id}") public R<?> edit(@PathVariable Long caseId,@PathVariable Long id,@RequestBody BankStatementService.Edit value){return R.ok(service.edit(caseId,id,value));}
    @PostMapping("/{id}/split") public R<?> split(@PathVariable Long caseId,@PathVariable Long id,@RequestBody BankStatementService.Split value){return R.ok(service.split(caseId,id,value));}
    @GetMapping("/{id}/source") public R<?> source(@PathVariable Long caseId,@PathVariable Long id){return R.ok(service.source(caseId,id));}
    @GetMapping("/{id}/history") public R<?> history(@PathVariable Long caseId,@PathVariable Long id){return R.ok(service.history(caseId,id));}
    @PostMapping("/pending") public R<?> pending(@PathVariable Long caseId,@RequestBody Pending value){service.pending(caseId,value.ids(),value.reason());return R.ok();}
    @PostMapping("/duplicates/{id}") public R<?> duplicate(@PathVariable Long caseId,@PathVariable Long id,@RequestBody BankStatementService.Duplicate value){service.decide(caseId,id,value);return R.ok();}
    @PostMapping("/export") public R<?> export(@PathVariable Long caseId,@RequestBody BankStatementExport.Request value){return R.ok(exports.request(caseId,value));}
    @GetMapping("/exports") public R<?> exports(@PathVariable Long caseId){return R.ok(exports.list(caseId));}
    @GetMapping("/exports/{id}") public R<?> status(@PathVariable Long caseId,@PathVariable Long id){return R.ok(exports.status(caseId,id));}
    @PostMapping("/exports/{id}/retry") public R<?> retry(@PathVariable Long caseId,@PathVariable Long id){exports.retry(caseId,id);return R.ok();}
    @GetMapping("/exports/{id}/download") public ResponseEntity<byte[]> download(@PathVariable Long caseId,@PathVariable Long id){var meta=exports.status(caseId,id);String format=meta.get("format").toString();return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename("bank-review-"+id+"."+format).build().toString()).contentType(MediaType.parseMediaType(format.equals("xlsx")?"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":"text/csv;charset=UTF-8")).body(exports.download(caseId,id));}
}
