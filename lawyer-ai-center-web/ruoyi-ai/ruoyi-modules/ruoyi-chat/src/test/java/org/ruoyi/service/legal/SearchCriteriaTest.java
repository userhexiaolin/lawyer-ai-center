package org.ruoyi.service.legal;
import org.junit.jupiter.api.*;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.domain.vo.knowledge.KnowledgeRetrievalVo;
import org.ruoyi.service.knowledge.LegalMetadataService;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("dev") class SearchCriteriaTest {
 @Test void combinesTermsDateRegionAndTopicAndExcludesMissingMetadata(){
  var hit=new KnowledgeRetrievalVo();hit.setContent("借款利息 违约金 调整");
  hit.setLegalMetadata(new LegalMetadataService.LegalMetadata("民法典","680","全国","有效",LocalDate.of(2020,5,28),null,"2020"));
  assertTrue(new SearchCriteria("利息 调整","2020-01-01","2020-12-31","全国","借款").accepts(hit));
  assertFalse(new SearchCriteria(null,"2021-01-01",null,null,null).accepts(hit));
  assertFalse(new SearchCriteria(null,null,null,"江西",null).accepts(hit));
  hit.setLegalMetadata(null);assertFalse(new SearchCriteria(null,"2020-01-01",null,null,null).accepts(hit));
 }
 @Test void sqlIsBoundAndDatesValidatedBeforeUsage(){
  var values=new ArrayList<Object>();String injection="' OR 1=1 --";
  String sql=new SearchCriteria(injection,null,null,"全国",null).sql(values);
  assertFalse(sql.contains(injection));assertTrue(sql.contains("a.region=?"));assertTrue(values.contains("全国"));
  assertThrows(ServiceException.class,()->new SearchCriteria(null,"2026-13-01",null,null,null));
  assertThrows(ServiceException.class,()->new SearchCriteria(null,"2026-10-18","2020-01-01",null,null));
 }
}
