package org.ruoyi.service.legal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.domain.entity.legal.LegalDocTemplate;
import org.ruoyi.mapper.legal.LegalDocTemplateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import java.util.Date;
import java.util.List;

/** Initialize query/JSON descriptors before the server accepts traffic; never preload tenant data. */
@Component
public class TemplateReadWarmup implements SmartInitializingSingleton {
    private static final Logger LOG=LoggerFactory.getLogger(TemplateReadWarmup.class);
    private final LegalDocTemplateMapper templates;
    private final ObjectMapper json;
    public TemplateReadWarmup(LegalDocTemplateMapper templates,ObjectMapper json){this.templates=templates;this.json=json;}

    @Override public void afterSingletonsInstantiated(){
        long start=System.nanoTime();
        try{
            // Always false, even outside a request tenant. No login, permission or tenant-ignore flags change.
            var query=new LambdaQueryWrapper<LegalDocTemplate>().eq(LegalDocTemplate::getTenantId,"__template_boot__").apply("1=0")
                .orderByAsc(LegalDocTemplate::getDocumentType,LegalDocTemplate::getSortOrder,LegalDocTemplate::getId);
            templates.selectPage(new Page<>(1,1),query);
            templates.selectPage(new Page<>(1,1,false),query);
            var field=new TemplateFields.Field("boot","p0",0,1,"_","初始化字段","text","","underline",true);
            var blocks=List.of(new TemplateLayout.Block("p0","_",true,List.of(field)));
            TemplateSnapshot.decode(TemplateSnapshot.encode(blocks,new TemplateLayout.State(false,true,"RULES",""),-1L),-1L);
            var row=new LegalDocTemplate();row.setId(-1L);row.setName("初始化样本");row.setTenantId("__template_boot__");row.setDocumentType("初始化样本");row.setContent("_");row.setStatus("1");row.setSortOrder(0);row.setRemark("");row.setBlocks(blocks);row.setFieldsConfirmed(true);row.setParseStatus("confirmed");row.setEditableBlocks(List.of("p0"));row.setSourceVersion("00000000-0000-0000-0000-000000000000");row.setSourceFormat("docx");row.setSourceFilename("bootstrap.docx");row.setAnalysisWarning("");row.setCreateTime(new Date(0));row.setUpdateTime(new Date(0));
            json.writeValueAsString(R.ok(row));
            json.writeValueAsString(R.ok(new DocTemplateService.Listing(List.of(row),1)));
            LOG.info("template_read_mapping_warmup completed elapsedMs={}",(System.nanoTime()-start)/1_000_000);
        }catch(Exception error){
            // An optional/uninstalled template module must not prevent unrelated modules from starting.
            LOG.warn("template_read_mapping_warmup skipped errorType={}",error.getClass().getSimpleName());
        }
    }
}
