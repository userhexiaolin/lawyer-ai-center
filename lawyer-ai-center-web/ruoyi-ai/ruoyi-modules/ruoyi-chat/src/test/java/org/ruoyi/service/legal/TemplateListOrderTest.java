package org.ruoyi.service.legal;
import org.junit.jupiter.api.*;import org.ruoyi.common.satoken.utils.LoginHelper;import org.ruoyi.domain.entity.legal.LegalDocTemplate;import org.ruoyi.mapper.legal.LegalDocTemplateMapper;import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;import com.baomidou.mybatisplus.extension.plugins.pagination.Page;import java.util.List;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
@Tag("dev") class TemplateListOrderTest {
 @Test void managementUsesGlobalDescendingRankBeforePaginationAndKeepsTenantAndFilters(){
  com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),"test"),LegalDocTemplate.class);
  var mapper=mock(LegalDocTemplateMapper.class);var types=mock(DocTemplateTypeService.class);when(types.require("律师函",false,false)).thenReturn("律师函");var service=new DocTemplateService(mapper,types);
  when(mapper.selectPage(any(Page.class),any(LambdaQueryWrapper.class))).thenAnswer(call->{Page<LegalDocTemplate> page=call.getArgument(0);LambdaQueryWrapper<LegalDocTemplate> query=call.getArgument(1);String sql=query.getSqlSegment();
   assertTrue(sql.endsWith("ORDER BY sort_order DESC,id ASC"),sql);assertFalse(sql.contains("document_type ASC"));assertEquals(2,page.getCurrent());assertEquals(2,page.getSize());assertTrue(query.getParamNameValuePairs().containsValue("tenant-a"));assertTrue(query.getParamNameValuePairs().containsValue("律师函"));assertTrue(sql.contains("name LIKE"));return new Page<LegalDocTemplate>(2,2,3).setRecords(List.of());
  });
  try(var login=mockStatic(LoginHelper.class)){login.when(LoginHelper::getUserId).thenReturn(7L);login.when(LoginHelper::getTenantId).thenReturn("tenant-a");assertEquals(3,service.list("律师函","合成",2,2).total());}
 }
}
