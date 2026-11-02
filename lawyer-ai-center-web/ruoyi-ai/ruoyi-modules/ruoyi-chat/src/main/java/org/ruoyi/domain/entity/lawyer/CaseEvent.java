package org.ruoyi.domain.entity.lawyer;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.ruoyi.common.tenant.core.TenantEntity;

@Data @EqualsAndHashCode(callSuper=true) @TableName("lawyer_case_event")
public class CaseEvent extends TenantEntity {
    @TableId @JsonFormat(shape=JsonFormat.Shape.STRING) private Long id;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long caseId;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long userId;
    private String title;
    private String summary;
    private String eventType;
    private String participantsJson;
    private String dateKind;
    private String dateRaw;
    private String dateStart;
    private String dateEnd;
    private String timezone;
    private String reviewStatus;
    private String sourceStatus;
    private String sourceErrors;
    private String origin;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long originClaimId;
    private String groupLabel;
    private Integer sortOrder;
    private Integer version;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long reviewerId;
    private java.time.LocalDateTime reviewedAt;
}
