package org.ruoyi.service.legal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.entity.legal.LegalDocTemplate;
import org.ruoyi.mapper.legal.LegalDocTemplateMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.ByteArrayOutputStream;
import java.sql.ResultSet;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class TemplateListSummaryTest {
    private byte[] docx(TemplateLayout.State state) throws Exception {
        byte[] source;
        try(var doc=new XWPFDocument();var out=new ByteArrayOutputStream()){
            doc.createParagraph().createRun().setText("合成模板正文");doc.write(out);source=out.toByteArray();
        }
        return state==null?source:TemplateLayout.storeState(source,state);
    }
    @Test void listLoadsOneSummaryBatchWithoutOpeningSourcesOrFields(){
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),"test"),LegalDocTemplate.class);
        var mapper=mock(LegalDocTemplateMapper.class);var types=mock(DocTemplateTypeService.class);var files=mock(TemplateFiles.class);
        var service=new DocTemplateService(mapper,types);ReflectionTestUtils.setField(service,"files",files);
        var row=new LegalDocTemplate();row.setId(9L);row.setStatus("1");
        when(mapper.selectPage(any(Page.class),any(LambdaQueryWrapper.class))).thenAnswer(call->{
            LambdaQueryWrapper<LegalDocTemplate> query=call.getArgument(1);
            assertTrue(query.getSqlSegment().contains("tenant_id"));assertTrue(query.getParamNameValuePairs().containsValue("tenant-a"));
            return new Page<LegalDocTemplate>(1,20,1).setRecords(List.of(row));
        });
        when(files.currentSummaries(List.of(9L))).thenReturn(Map.of(9L,new TemplateFiles.SourceSummary("version","synthetic.docx","docx","parsed","待核对",false)));
        try(var login=mockStatic(LoginHelper.class)){
            login.when(LoginHelper::getUserId).thenReturn(7L);login.when(LoginHelper::getTenantId).thenReturn("tenant-a");
            var result=service.list(null,null,1,20);assertEquals(1,result.total());assertEquals("version",row.getSourceVersion());assertEquals("parsed",row.getParseStatus());assertFalse(row.isFieldsConfirmed());assertNull(row.getBlocks());
            verify(files).currentSummaries(List.of(9L));verifyNoMoreInteractions(files);
        }
    }
    @Test void summaryQueryScopesBothVersionSelectionAndRowsAndKeepsReviewStates()throws Exception{
        var jdbc=mock(JdbcTemplate.class);var files=new TemplateFiles(jdbc,null);
        var raw=docx(new TemplateLayout.State(true,false,"RAW","未解析"));
        var parsed=docx(new TemplateLayout.State(false,false,"AI","待人工核对"));
        var confirmed=docx(new TemplateLayout.State(false,true,"AI",""));
        var legacy=docx(null);
        doAnswer(call->{
            String sql=call.getArgument(0);assertTrue(sql.contains("f.tenant_id=?"));assertTrue(sql.contains("latest.tenant_id=f.tenant_id"));assertTrue(sql.contains("latest.template_id=f.template_id"));assertTrue(sql.contains("ORDER BY latest.bound_at DESC,latest.version_id DESC LIMIT 1"));
            assertFalse(sql.contains("source_bytes"));assertFalse(sql.contains("blocks_json"));assertFalse(sql.contains("docx_bytes"));assertTrue(sql.contains("JSON_EXTRACT"));assertTrue(sql.contains("IN (?,?,?,?)"));
            assertEquals("tenant-a",call.getArgument(2));assertEquals(Long.valueOf(9L),call.<Long>getArgument(3));
            RowCallbackHandler callback=call.getArgument(1);
            for(int i=0;i<4;i++){
                var rs=mock(ResultSet.class);when(rs.getLong("template_id")).thenReturn(9L+i);when(rs.getString("snapshot_schema")).thenReturn("1");when(rs.getString("parse_status")).thenReturn(new String[]{"uploaded","parsed","confirmed","confirmed"}[i]);when(rs.getString("fields_confirmed")).thenReturn(i>=2?"true":"false");when(rs.getString("version_id")).thenReturn("v"+i);callback.processRow(rs);verify(rs,never()).getBytes(anyString());
            }
            return null;
        }).when(jdbc).query(anyString(),any(RowCallbackHandler.class),any(Object[].class));
        try(var login=mockStatic(LoginHelper.class)){
            login.when(LoginHelper::getUserId).thenReturn(7L);login.when(LoginHelper::getTenantId).thenReturn("tenant-a");
            var result=files.currentSummaries(List.of(9L,10L,11L,12L,9L));
            assertEquals("uploaded",result.get(9L).parseStatus());assertFalse(result.get(9L).fieldsConfirmed());
            assertEquals("parsed",result.get(10L).parseStatus());assertFalse(result.get(10L).fieldsConfirmed());
            assertEquals("confirmed",result.get(11L).parseStatus());assertTrue(result.get(11L).fieldsConfirmed());
            assertEquals("confirmed",result.get(12L).parseStatus());assertTrue(result.get(12L).fieldsConfirmed());
            verify(jdbc,times(1)).query(anyString(),any(RowCallbackHandler.class),any(Object[].class));
        }
    }
    @Test void summaryRejectsMissingLoginAndCorruptStateWithoutClaimingConfirmation()throws Exception{
        var jdbc=mock(JdbcTemplate.class);var files=new TemplateFiles(jdbc,null);
        try(var login=mockStatic(LoginHelper.class)){assertEquals(401,assertThrows(DraftingException.class,()->files.currentSummaries(List.of(9L))).getCode());}
        assertEquals(422,assertThrows(DraftingException.class,()->TemplateLayout.state(new byte[]{1,2,3})).getCode());
        verifyNoInteractions(jdbc);
    }
}
