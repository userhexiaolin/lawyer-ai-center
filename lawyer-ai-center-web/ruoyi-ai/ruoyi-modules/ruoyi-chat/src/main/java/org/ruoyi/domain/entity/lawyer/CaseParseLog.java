package org.ruoyi.domain.entity.lawyer;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.ruoyi.common.tenant.core.TenantEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lawyer_case_parse_log")
public class CaseParseLog extends TenantEntity {
    @TableId @JsonFormat(shape=JsonFormat.Shape.STRING) private Long id;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long caseId;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long fileVersionId;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long jobId;
    private Integer pageIndex;
    private String action;
    private String beforeStatus;
    private String afterStatus;
    private String errorCode;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long operatorId;
}
