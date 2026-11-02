package org.ruoyi.controller.lawyer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.domain.bo.lawyer.CaseInput;
import org.ruoyi.domain.entity.lawyer.LawyerCase;
import org.ruoyi.service.lawyer.LawyerCaseService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/lawyer/case")
public class LawyerCaseController {
    private final LawyerCaseService service;
    @ExceptionHandler(org.ruoyi.common.core.exception.ServiceException.class)
    public R<Void> caseError(org.ruoyi.common.core.exception.ServiceException error){return R.fail(error.getCode()==null?500:error.getCode(),error.getMessage());}
    @ExceptionHandler(java.io.IOException.class)
    public R<Void> downloadError(java.io.IOException error){return R.fail(503,"材料下载失败：文件存储暂不可用，请稍后重试");}
    @GetMapping("/upcoming") public R<java.util.List<org.ruoyi.service.lawyer.CaseReminder>> upcoming(){return R.ok(service.upcoming());}
    public record SessionLink(@jakarta.validation.constraints.NotNull Long sessionId) {}
    public record FileLink(@jakarta.validation.constraints.NotNull Long ossId) {}
    @PostMapping public R<Long> create(@Valid @RequestBody CaseInput input){return R.ok(service.create(input));}
    @PutMapping public R<Void> update(@Valid @RequestBody CaseInput input){service.update(input);return R.ok();}
    @GetMapping("/{id}") public R<LawyerCaseService.CaseDetail> detail(@PathVariable Long id){return R.ok(service.detail(id));}
    @GetMapping("/list") public TableDataInfo<LawyerCase> list(@RequestParam(required=false)String keyword,@RequestParam(required=false)String status,@RequestParam(required=false)String caseType,@RequestParam(defaultValue="1")int pageNum,@RequestParam(defaultValue="20")int pageSize){return service.list(keyword,status,caseType,pageNum,pageSize);}
    @DeleteMapping("/{id}") public R<Void> delete(@PathVariable Long id){service.delete(id);return R.ok();}
    @PostMapping("/{id}/sessions") public R<Void> linkSession(@PathVariable Long id,@Valid @RequestBody SessionLink input){service.linkSession(id,input.sessionId());return R.ok();}
    @DeleteMapping("/{id}/sessions/{sessionId}") public R<Void> unlinkSession(@PathVariable Long id,@PathVariable Long sessionId){service.unlinkSession(id,sessionId);return R.ok();}
    @PostMapping("/{id}/files") public R<Void> linkFile(@PathVariable Long id,@Valid @RequestBody FileLink input){service.linkFile(id,input.ossId());return R.ok();}
    @GetMapping("/{id}/files") public R<java.util.List<LawyerCaseService.Material>> files(@PathVariable Long id){return R.ok(service.files(id));}
    @GetMapping("/{id}/files/{ossId}") public R<LawyerCaseService.Material> file(@PathVariable Long id,@PathVariable Long ossId){return R.ok(service.file(id,ossId));}
    @GetMapping("/{id}/files/{ossId}/download") public void download(@PathVariable Long id,@PathVariable Long ossId,jakarta.servlet.http.HttpServletResponse response)throws java.io.IOException{service.download(id,ossId,response);}
    @DeleteMapping("/{id}/files/{ossId}") public R<Void> unlinkFile(@PathVariable Long id,@PathVariable Long ossId){service.unlinkFile(id,ossId);return R.ok();}
}
