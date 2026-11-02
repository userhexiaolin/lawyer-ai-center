package org.ruoyi.system.controller.system;

import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.system.service.PersonalNoticeService;
import org.springframework.web.bind.annotation.*;

/** Authenticated inbox; recipient/tenant are never accepted from request data. */
@RestController @RequiredArgsConstructor @RequestMapping("/system/notice/inbox")
public class PersonalNoticeController {
    private final PersonalNoticeService service;
    @ExceptionHandler(org.ruoyi.common.core.exception.ServiceException.class)
    public R<Void> error(org.ruoyi.common.core.exception.ServiceException e){return R.fail(e.getCode()==null?500:e.getCode(),e.getMessage());}
    @GetMapping public R<PersonalNoticeService.Inbox> list(@RequestParam(required=false) Long beforeId,@RequestParam(defaultValue="20") int limit){
        return R.ok(service.list(LoginHelper.getTenantId(),LoginHelper.getUserId(),beforeId,limit));
    }
    @PutMapping("/{id}/read") public R<Void> read(@PathVariable Long id){service.markRead(LoginHelper.getTenantId(),LoginHelper.getUserId(),id);return R.ok();}
}
