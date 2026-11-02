package org.ruoyi.domain.entity.legal;
import lombok.*;
import com.baomidou.mybatisplus.annotation.*;
import org.ruoyi.common.tenant.core.TenantEntity;
@Data @EqualsAndHashCode(callSuper=true) @TableName("legal_doc_template")
public class LegalDocTemplate extends TenantEntity {
 @TableId(type=IdType.ASSIGN_ID) private Long id;
 private String name;
 private String documentType;
 private String content;
 private Integer sortOrder;
 /** 0 enabled, 1 disabled, same convention as other administration pages. */
 private String status;
 private String remark;
 @TableField(exist=false) private String expectedRevision;
 @TableField(exist=false) private org.ruoyi.service.legal.TemplateFieldTask.Status fieldAnalysis;
 @TableField(exist=false) private String sourceVersion;
 @TableField(exist=false) private String sourceFormat;
 @TableField(exist=false) private String sourceFilename;
 @TableField(exist=false) private String parseStatus;
 @TableField(exist=false) private String analysisWarning;
 @TableField(exist=false) private boolean fieldsConfirmed;
 @TableField(exist=false) private java.util.List<String> editableBlocks;
 @TableField(exist=false) private java.util.List<org.ruoyi.service.legal.TemplateLayout.Block> blocks;
}
