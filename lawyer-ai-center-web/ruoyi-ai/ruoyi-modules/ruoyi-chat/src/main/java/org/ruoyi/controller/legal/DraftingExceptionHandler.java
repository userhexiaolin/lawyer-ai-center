package org.ruoyi.controller.legal;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.service.legal.DraftingException;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class DraftingExceptionHandler {
    @ExceptionHandler(DraftingException.class)
    public R<Void> business(DraftingException error){return R.fail(error.getCode(),error.getMessage());}
}
