package org.ruoyi.domain.entity.lawyer;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.ruoyi.common.tenant.core.TenantEntity;

@Data @EqualsAndHashCode(callSuper=true) @TableName("lawyer_case_source_anchor")
public class CaseSourceAnchor extends TenantEntity {
    @TableId @JsonFormat(shape=JsonFormat.Shape.STRING) private Long id;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long caseId;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long userId;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long fileVersionId;
    @JsonFormat(shape=JsonFormat.Shape.STRING) private Long pageId;
    private Integer pageIndex;
    private String kind;
    private Integer fragmentStart;
    private Integer fragmentEnd;
    private String fragmentText;
    private String pageHash;
    private String fileHash;
    private String imageHash;
    private String regionHash;
    private String geometryJson;
    private Integer tableRow;
    private Integer tableColumn;
    private Boolean pinnedHistory;
    private String fingerprint;
}
