package org.ruoyi.controller.legal;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.domain.bo.legal.SearchReportInput.*;
import org.ruoyi.domain.vo.legal.SearchReportView.*;
import org.ruoyi.service.legal.SearchReportService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/legal/report")
public class SearchReportController {
    private final SearchReportService service;
    // Global advice intentionally masks generic service messages. These route-local errors are
    // controlled business messages (never raw SQL/OSS exceptions) and must remain actionable.
    @ExceptionHandler(ServiceException.class)
    public R<Void> reportError(ServiceException error) {
        return R.fail(error.getCode() == null ? 500 : error.getCode(), error.getMessage());
    }
    @ExceptionHandler(IOException.class)
    public R<Void> downloadError(IOException error) { return R.fail(503, "报告下载失败：文件存储暂不可用，请稍后重试"); }
    @PostMapping public R<Report> create(@Valid @RequestBody Create input) { return R.ok(service.create(Owner.current(), input)); }
    @GetMapping("/list") public TableDataInfo<Report> list(@RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "20") int pageSize) {
        return service.list(Owner.current(), pageNum, pageSize);
    }
    @GetMapping("/{id}") public R<Detail> detail(@PathVariable @Positive Long id) { return R.ok(service.detail(Owner.current(), id)); }
    @PutMapping("/{id}") public R<Report> update(@PathVariable @Positive Long id, @Valid @RequestBody Update input) { return R.ok(service.update(Owner.current(), id, input)); }
    @DeleteMapping("/{id}") public R<Void> delete(@PathVariable @Positive Long id) { service.delete(Owner.current(), id); return R.ok(); }
    @PostMapping("/{id}/items") public R<Added> add(@PathVariable @Positive Long id, @Valid @RequestBody AddItem input) { return R.ok(service.add(Owner.current(), id, input)); }
    @PutMapping("/{id}/items/order") public R<Void> order(@PathVariable @Positive Long id, @Valid @RequestBody Order input) { service.order(Owner.current(), id, input); return R.ok(); }
    @DeleteMapping("/{id}/items/{itemId}") public R<Void> deleteItem(@PathVariable @Positive Long id, @PathVariable @Positive Long itemId) { service.deleteItem(Owner.current(), id, itemId); return R.ok(); }
    @PostMapping("/{id}/generate") public R<Generated> generate(@PathVariable @Positive Long id) {
        try { return R.ok(service.generate(Owner.current(), id)); }
        catch (ServiceException e) { throw e; }
        catch (Exception e) { throw new ServiceException("报告生成失败：无法保存生成结果，请稍后重试", 500); }
    }
    @GetMapping("/{id}/download") public void download(@PathVariable @Positive Long id, HttpServletResponse response) throws IOException {
        service.download(Owner.current(), id, response);
    }
}
