package org.ruoyi.controller.chat;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.service.chat.LawyerUsageService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequiredArgsConstructor
@RequestMapping("/lawyer/usage")
public class LawyerUsageController {
    private final LawyerUsageService service;
    @GetMapping public R<LawyerUsageService.Usage> usage() { return R.ok(service.read()); }
}
