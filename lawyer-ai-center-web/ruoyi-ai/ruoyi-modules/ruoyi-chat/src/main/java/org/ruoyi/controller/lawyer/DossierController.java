package org.ruoyi.controller.lawyer;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.service.lawyer.DossierService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequiredArgsConstructor
@RequestMapping("/lawyer/case/{caseId}/dossier")
public class DossierController {
    private final DossierService service;
    @ExceptionHandler(org.ruoyi.common.core.exception.ServiceException.class)
    public R<Void> dossierError(org.ruoyi.common.core.exception.ServiceException error){
        Integer code=error.getCode();
        return R.fail(code!=null&&java.util.Set.of(401,403,404,429).contains(code)?code:400,error.getMessage());
    }
    public record Input(List<Long> fileIds) {}
    @PostMapping("/parse") public R<DossierService.Bundle> parse(@PathVariable Long caseId,@RequestBody Input input){return R.ok(service.parse(caseId,input.fileIds()));}
}
