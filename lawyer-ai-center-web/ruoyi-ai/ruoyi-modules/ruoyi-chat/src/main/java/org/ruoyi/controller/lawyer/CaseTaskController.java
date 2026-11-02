package org.ruoyi.controller.lawyer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.domain.bo.lawyer.CaseTaskInput;
import org.ruoyi.domain.vo.lawyer.CaseTaskView.*;
import org.ruoyi.service.lawyer.CaseTaskService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @Validated @RequiredArgsConstructor
@RequestMapping("/lawyer/case")
public class CaseTaskController {
    private final CaseTaskService service;
    @ExceptionHandler(ServiceException.class) public R<Void> error(ServiceException error) { return R.fail(error.getCode()==null?500:error.getCode(),error.getMessage()); }
    @GetMapping("/{id}/tasks") public R<Summary> list(@PathVariable @Positive Long id) { return R.ok(service.list(id)); }
    @PostMapping("/{id}/tasks") public R<Task> create(@PathVariable @Positive Long id,@Valid @RequestBody CaseTaskInput.Create input) { return R.ok(service.create(id,input)); }
    @PatchMapping("/task/{taskId}") public R<Task> patch(@PathVariable @Positive Long taskId,@Valid @RequestBody CaseTaskInput.Patch input) { return R.ok(service.patch(taskId,input)); }
    @PostMapping("/task/{taskId}/checklist") public R<Checklist> add(@PathVariable @Positive Long taskId,@Valid @RequestBody CaseTaskInput.Checklist input) { return R.ok(service.addChecklist(taskId,input)); }
    @PostMapping("/task/{taskId}/checklist/{itemId}/toggle") public R<Checklist> toggle(@PathVariable @Positive Long taskId,@PathVariable @Positive Long itemId,@Valid @RequestBody(required=false) CaseTaskInput.Toggle input) { return R.ok(service.toggle(taskId,itemId,input==null?null:input.checked())); }
    @PostMapping("/task/{taskId}/time") public R<Task> time(@PathVariable @Positive Long taskId,@Valid @RequestBody CaseTaskInput.Time input) { return R.ok(service.addTime(taskId,input)); }
}
