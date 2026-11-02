package org.ruoyi.mapper.legal;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import org.ruoyi.domain.entity.legal.LegalDocTemplateType;
import java.util.List;

@Mapper
public interface LegalDocTemplateTypeMapper extends BaseMapper<LegalDocTemplateType> {
    // Unique (tenant_id, name) makes concurrent first access safe without resetting edits.
    @Insert("""
        <script>
        INSERT IGNORE INTO legal_doc_template_type(id,tenant_id,name,sort_order,status,builtin,remark)
        VALUES <foreach collection="rows" item="row" separator=",">
        (#{row.id},#{row.tenantId},#{row.name},#{row.sortOrder},'0',1,'系统内置类型')
        </foreach>
        </script>
        """)
    int insertDefaults(@Param("rows") List<LegalDocTemplateType> rows);
}
