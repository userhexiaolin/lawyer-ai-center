package org.ruoyi.domain.entity.lawyer;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.ruoyi.common.tenant.core.TenantEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lawyer_case_upload")
public class CaseUpload extends TenantEntity {
    @TableId @JsonFormat(shape=JsonFormat.Shape.STRING) private Long id;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long caseId;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long userId;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long materialId;
    private String fileName;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long fileBytes;
    private String sha256;
    private String idempotencyKey;
    private Boolean keepDuplicate;
    private String state;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long ossId;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long fileVersionId;
    private String mime;
    private java.time.LocalDateTime leaseUntil;
}
