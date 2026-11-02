package org.ruoyi.controller.lawyer;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.service.lawyer.DossierReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @SaCheckLogin @RequiredArgsConstructor
@RequestMapping("/lawyer/case/{caseId}/dossier/review")
public class DossierReviewController {
    private final DossierReviewService service;
    @ExceptionHandler(ServiceException.class) public ResponseEntity<R<Void>> error(ServiceException e){int code=e.getCode()==null?500:e.getCode();return ResponseEntity.status(code>=400&&code<=599?code:500).body(R.fail(code,e.getMessage()));}
    @ExceptionHandler(Exception.class) public ResponseEntity<R<Void>> unavailable(){return ResponseEntity.status(500).body(R.fail(500,"核对未完成，输入与原件保留，请重试"));}
    @GetMapping("/resolve") public R<?> resolve(@PathVariable Long caseId,@ModelAttribute DossierReviewService.Pointer pointer){return R.ok(service.resolve(caseId,pointer));}
    public record Queue(String kind,java.util.List<Long> ids){}
    @PostMapping("/queue") public R<?> queue(@PathVariable Long caseId,@RequestBody Queue value){return R.ok(service.queue(caseId,value.kind(),value.ids()));}
    @PatchMapping("/{kind}/{id}") public R<?> save(@PathVariable Long caseId,@PathVariable String kind,@PathVariable Long id,@RequestBody DossierReviewService.Save value){return R.ok(service.save(caseId,kind,id,value));}
}
