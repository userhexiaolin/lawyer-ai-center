package org.ruoyi.controller.lawyer;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.service.lawyer.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @SaCheckLogin @RequiredArgsConstructor
@RequestMapping("/lawyer/case/{caseId}/dossier")
public class DossierClaimController {
    private final DossierAnchorService anchors;
    private final DossierClaimService claims;
    private final DossierClaimExtractor extraction;
    @ExceptionHandler(ServiceException.class) public ResponseEntity<R<Void>> error(ServiceException e){int code=e.getCode()==null?500:e.getCode();if(code<400||code>599)code=500;return ResponseEntity.status(code).body(R.fail(code,e.getMessage()));}
    @ExceptionHandler(Exception.class) public ResponseEntity<R<Void>> unavailable(){return ResponseEntity.status(500).body(R.fail(500,"处理未完成，请重试；原件与人工输入保留"));}
    @PostMapping("/anchors") public R<?> anchor(@PathVariable Long caseId,@RequestBody DossierAnchorService.Create value){return R.ok(anchors.create(caseId,value));}
    @GetMapping("/anchors/{id}/resolve") public R<?> resolve(@PathVariable Long caseId,@PathVariable Long id){return R.ok(anchors.resolve(caseId,id));}
    @GetMapping("/claims") public R<?> list(@PathVariable Long caseId,@ModelAttribute DossierClaimService.Filter filter){return R.ok(claims.list(caseId,filter));}
    @GetMapping("/claims/{id}") public R<?> detail(@PathVariable Long caseId,@PathVariable Long id){return R.ok(claims.detail(caseId,id));}
    @PostMapping("/claims") public R<?> manual(@PathVariable Long caseId,@RequestBody DossierClaimService.Edit value){return R.ok(claims.manual(caseId,value));}
    @PatchMapping("/claims/{id}") public R<?> edit(@PathVariable Long caseId,@PathVariable Long id,@RequestBody DossierClaimService.Edit value){return R.ok(claims.edit(caseId,id,value));}
    @PostMapping("/claims/{id}/split") public R<?> split(@PathVariable Long caseId,@PathVariable Long id,@RequestBody DossierClaimService.Split value){return R.ok(claims.split(caseId,id,value));}
    @PostMapping("/claims/merge") public R<?> merge(@PathVariable Long caseId,@RequestBody DossierClaimService.Combine value){return R.ok(claims.merge(caseId,value));}
    @PostMapping("/claims/{id}/suggestions/{suggestionId}") public R<?> suggestion(@PathVariable Long caseId,@PathVariable Long id,@PathVariable Long suggestionId,@RequestBody DossierClaimService.Decision value){return R.ok(claims.decideSuggestion(caseId,id,suggestionId,value));}
    @PostMapping("/claims/extract") public R<?> extract(@PathVariable Long caseId,@RequestBody DossierClaimExtractor.Request value){return R.ok(extraction.request(caseId,value));}
    @GetMapping("/claims/models") public R<?> models(@PathVariable Long caseId){return R.ok(extraction.models(caseId));}
    @GetMapping("/claims/budget") public R<?> budget(@PathVariable Long caseId){return R.ok(extraction.budget(caseId));}
    @PostMapping("/claims/runs/{id}/cancel") public R<?> cancel(@PathVariable Long caseId,@PathVariable Long id){extraction.cancel(caseId,id);return R.ok();}
    @GetMapping("/claims/runs") public R<?> runs(@PathVariable Long caseId){return R.ok(extraction.runs(caseId));}
    @GetMapping("/claims/runs/{id}/chunks") public R<?> chunks(@PathVariable Long caseId,@PathVariable Long id){return R.ok(extraction.chunks(caseId,id));}
    @PostMapping("/claims/runs/{id}/retry") public R<?> retry(@PathVariable Long caseId,@PathVariable Long id){extraction.retry(caseId,id);return R.ok();}
}
