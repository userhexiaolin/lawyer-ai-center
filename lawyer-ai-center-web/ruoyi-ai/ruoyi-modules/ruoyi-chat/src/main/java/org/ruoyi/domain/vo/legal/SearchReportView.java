package org.ruoyi.domain.vo.legal;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.satoken.utils.LoginHelper;
import java.time.LocalDateTime;
import java.util.List;

public final class SearchReportView {
    private SearchReportView() {}
    /** Constructed from the authenticated context, never bound from request JSON. */
    public record Owner(String tenantId, Long userId) {
        public Owner {
            if (tenantId == null || tenantId.isBlank() || userId == null) throw new ServiceException("请先登录", 401);
        }
        public static Owner current() { return new Owner(LoginHelper.getTenantId(), LoginHelper.getUserId()); }
    }
    public record Report(@JsonFormat(shape = JsonFormat.Shape.STRING) Long id,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long userId, String title, String status,
        String background, String conclusion, LocalDateTime createdAt, LocalDateTime updatedAt,
        LocalDateTime generatedAt, @JsonIgnore Long fileOssId) {
        public String getFileUrl() { return "generated".equals(status) && fileOssId != null ? downloadUrl(id) : null; }
    }
    public record Item(@JsonFormat(shape = JsonFormat.Shape.STRING) Long id,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long reportId, String itemType, String refId,
        String sectionKey, String snapshotTitle, String snapshotContent, String lawStatus,
        String lawName, String articleNo, String sourceUrl, int sortOrder, LocalDateTime createdAt) {}
    public record Detail(Report report, List<Item> items) {}
    public record Added(Item item, boolean duplicate) {}
    public record Generated(@JsonFormat(shape = JsonFormat.Shape.STRING) Long reportId, String status, String fileUrl) {}
    public static String downloadUrl(Long id) { return "/legal/report/" + id + "/download"; }
}
