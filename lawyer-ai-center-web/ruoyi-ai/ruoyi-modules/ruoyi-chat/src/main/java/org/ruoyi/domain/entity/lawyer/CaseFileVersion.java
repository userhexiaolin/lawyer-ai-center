package org.ruoyi.domain.entity.lawyer;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.ruoyi.common.tenant.core.TenantEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lawyer_case_file_version")
public class CaseFileVersion extends TenantEntity {
    @TableId @JsonFormat(shape=JsonFormat.Shape.STRING) private Long id;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long caseId;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long userId;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long materialId;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long ossId;
    private Integer versionNo;
    private String fileName;
    private String sha256;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long fileBytes;
    private String mime;
    private String uploadStatus;
    private String parseStatus;
    private Integer pageCount;
    private Integer parsedPages;
    private Integer failedPages;
    private String parserVersion;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long lastJobId;
    private String errorCode;
    private String errorMessage;
}
