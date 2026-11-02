package org.ruoyi.domain.entity.lawyer;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.ruoyi.common.tenant.core.TenantEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lawyer_case_parse_job")
public class CaseParseJob extends TenantEntity {
    @TableId @JsonFormat(shape=JsonFormat.Shape.STRING) private Long id;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long caseId;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long userId;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long fileVersionId;
    private String jobType;
    private String idempotencyKey;
    private String requestedPages;
    private String state;
    private Integer progressDone;
    private Integer progressTotal;
    private Integer attempts;
    @com.fasterxml.jackson.annotation.JsonIgnore private String leaseToken;
    private java.time.LocalDateTime leaseUntil;
    private String errorCode;
    private String errorMessage;
}
