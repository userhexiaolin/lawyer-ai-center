package org.ruoyi.domain.entity.lawyer;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.ruoyi.common.tenant.core.TenantEntity;

@Data @EqualsAndHashCode(callSuper=true) @TableName("lawyer_case_claim")
public class CaseClaim extends TenantEntity {
    @TableId @JsonFormat(shape=JsonFormat.Shape.STRING) private Long id;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long caseId;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long userId;
    private String kind;
    private String claimText;
    private String originalText;
    private String valuesJson;
    private String risk;
    private String reviewStatus;
    private String sourceStatus;
    private String sourceErrors;
    private String originKey;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long lastRunId;
    private Boolean edited;
    private Integer version;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long reviewerId;
    private java.time.LocalDateTime reviewedAt;
}
