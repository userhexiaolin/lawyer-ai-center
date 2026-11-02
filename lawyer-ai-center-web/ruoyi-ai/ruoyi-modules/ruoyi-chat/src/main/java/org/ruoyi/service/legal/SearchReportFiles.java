package org.ruoyi.service.legal;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.domain.dto.OssDTO;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.service.OssService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.io.File;
import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchReportFiles {
    private final OssService oss;
    // Keep sys_oss metadata committed independently so a report rollback can clean up the upload.
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public OssDTO upload(File file) {
        try {
            var result = oss.uploadFile(file);
            if (result == null || result.getOssId() == null) throw new IllegalStateException("Missing OSS identity");
            return result;
        } catch (Exception e) {
            log.warn("search_report_upload status=FAILED errorType={}", e.getClass().getSimpleName());
            throw new ServiceException("报告生成失败：文件存储不可用，请稍后重试", 503);
        }
    }
    public void discard(Long ossId) {
        try { if (!Boolean.TRUE.equals(oss.deleteFile(ossId))) log.warn("search_report_cleanup status=FAILED ossId={}", ossId); }
        catch (Exception e) { log.warn("search_report_cleanup status=FAILED ossId={} errorType={}", ossId, e.getClass().getSimpleName()); }
    }
    public void download(Long ossId, HttpServletResponse response) throws IOException { oss.downloadFile(ossId, response); }
}
