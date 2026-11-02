package org.ruoyi.controller.chat;
import org.ruoyi.common.core.domain.R;
import org.springframework.web.bind.annotation.*;
/** Ownership denial is an expected client response, not a server crash. */
@RestControllerAdvice(assignableTypes = {ChatSessionController.class, ChatMessageController.class, ContractReviewReportController.class})
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class PersonalDataExceptionAdvice {
    @ExceptionHandler(IllegalArgumentException.class)
    public R<Void> unavailable(IllegalArgumentException ignored) { return R.fail(403, "会话或消息不可用，请返回历史重新选择"); }
}
