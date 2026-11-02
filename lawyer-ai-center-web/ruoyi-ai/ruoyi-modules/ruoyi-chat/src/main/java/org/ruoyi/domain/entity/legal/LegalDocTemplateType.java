package org.ruoyi.domain.entity.legal;

import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import org.ruoyi.common.tenant.core.TenantEntity;

@Data @EqualsAndHashCode(callSuper = true) @TableName("legal_doc_template_type")
public class LegalDocTemplateType extends TenantEntity {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private String name;
    private Integer sortOrder;
    private String status;
    /** Built-in names are referenced by dedicated drafting workflows. */
    private Boolean builtin;
    private String remark;
}
