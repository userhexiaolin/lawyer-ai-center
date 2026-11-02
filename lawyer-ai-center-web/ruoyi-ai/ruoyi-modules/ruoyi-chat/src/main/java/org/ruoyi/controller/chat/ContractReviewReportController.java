package org.ruoyi.controller.chat;

import lombok.RequiredArgsConstructor;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.service.chat.ChatSessionOwnershipGuard;
import org.ruoyi.service.chat.ContractReviewReport;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/system/contract-review")
public class ContractReviewReportController {
    private final ChatSessionOwnershipGuard ownershipGuard;
    public record ReportRequest(Long sessionId, String content) {}
    @PostMapping("/report")
    public ResponseEntity<byte[]> report(@RequestBody ReportRequest request) {
        ownershipGuard.requireOwned(LoginHelper.getUserId(), request.sessionId());
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=contract-review-report.docx")
            .body(ContractReviewReport.generate(request.content()));
    }
}
