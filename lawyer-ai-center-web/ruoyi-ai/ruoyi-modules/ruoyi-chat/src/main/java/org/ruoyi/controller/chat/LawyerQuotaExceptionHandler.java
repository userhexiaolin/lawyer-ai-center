package org.ruoyi.controller.chat;

import jakarta.servlet.http.HttpServletRequest;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.web.handler.GlobalExceptionHandler;
import org.ruoyi.service.chat.LawyerUsageService;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Expose only the fixed quota notice; all other service errors retain global redaction. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "org.ruoyi.controller.chat")
public class LawyerQuotaExceptionHandler {
    private final GlobalExceptionHandler fallback = new GlobalExceptionHandler();

    @ExceptionHandler(ServiceException.class)
    public R<Void> handle(ServiceException error, HttpServletRequest request) {
        if (Integer.valueOf(429).equals(error.getCode()) && LawyerUsageService.EXHAUSTED.equals(error.getMessage())) {
            return R.fail(429, LawyerUsageService.EXHAUSTED);
        }
        return fallback.handleServiceException(error, request);
    }
}
