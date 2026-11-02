package org.ruoyi.domain.entity.lawyer;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.ruoyi.common.tenant.core.TenantEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lawyer_case_file_page")
public class CaseFilePage extends TenantEntity {
    @TableId @JsonFormat(shape=JsonFormat.Shape.STRING) private Long id;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long caseId;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long fileVersionId;
    private Integer pageIndex;
    private String pageLabel;
    private String extractStatus;
    private String ocrStatus;
    private String previewStatus;
    private String extractionMethod;
    private String textContent;
    private Integer charCount;
    private String textHash;
    private Boolean emptyPageConfirmed;
    private String errorCode;
    private String errorMessage;
    private String parserVersion;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long lastJobId;
    private Integer version;
}
