package org.ruoyi.service.legal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.ruoyi.domain.entity.legal.LegalDocTemplate;
import org.ruoyi.mapper.legal.LegalDocTemplateMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@Tag("dev")
class TemplateReadWarmupTest {
 @Test void initializesMappingsWithFalsePredicateAndSyntheticDataOnly(){
  com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),"test"),LegalDocTemplate.class);
  var mapper=mock(LegalDocTemplateMapper.class);var json=spy(new ObjectMapper());
  try(var word=mockStatic(TemplateLayout.class,call->{throw new AssertionError("Word read during initialization");});var zip=mockStatic(TemplateStateReader.class,call->{throw new AssertionError("ZIP read during initialization");})){
   new TemplateReadWarmup(mapper,json).afterSingletonsInstantiated();
  }
  verify(mapper,times(2)).selectPage(any(Page.class),any(LambdaQueryWrapper.class));
  for(var call:mockingDetails(mapper).getInvocations()){LambdaQueryWrapper<LegalDocTemplate> query=call.getArgument(1);assertTrue(query.getSqlSegment().contains("1=0"));assertTrue(query.getParamNameValuePairs().containsValue("__template_boot__"));}
  verifyNoMoreInteractions(mapper);assertTrue(mockingDetails(json).getInvocations().stream().filter(call->call.getMethod().getName().equals("writeValueAsString")).count()>=2);
 }
 @Test void missingOptionalTableDoesNotPreventOtherModulesStarting(){var mapper=mock(LegalDocTemplateMapper.class);when(mapper.selectPage(any(Page.class),any(LambdaQueryWrapper.class))).thenThrow(new IllegalStateException("Optional table not installed"));assertDoesNotThrow(()->new TemplateReadWarmup(mapper,new ObjectMapper()).afterSingletonsInstantiated());}
}
