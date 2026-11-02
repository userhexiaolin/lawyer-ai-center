package org.ruoyi.service.legal;

import org.junit.jupiter.api.*;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.vo.knowledge.*;
import org.ruoyi.service.knowledge.*;
import org.ruoyi.service.retrieval.KnowledgeRetrievalService;
import org.springframework.jdbc.core.*;
import org.springframework.dao.DataAccessResourceFailureException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("dev")
class FactSearchKnowledgeTest {
    KnowledgeAccess access=mock(KnowledgeAccess.class);IKnowledgeInfoService info=mock(IKnowledgeInfoService.class);KnowledgeRetrievalService retrieval=mock(KnowledgeRetrievalService.class);LegalMetadataService metadata=mock(LegalMetadataService.class);
    List<String> sql=new ArrayList<>();List<Object[]> bindings=new ArrayList<>();
    KnowledgeRetrievalVo hit=new KnowledgeRetrievalVo();
    JdbcTemplate jdbc=new JdbcTemplate(){@Override public <T> List<T> query(String statement,RowMapper<T> mapper,Object... args){sql.add(statement);bindings.add(args);if(statement.contains("MATCH("))throw new DataAccessResourceFailureException("no index");return (List<T>)List.of(hit);}};
    FactSearchKnowledge service=new FactSearchKnowledge(access,info,retrieval,metadata,jdbc);
    @BeforeEach void setup(){when(access.readableIds()).thenReturn(List.of(1L));hit.setId("fid");hit.setKnowledgeId(1L);hit.setContent("借款真实片段");hit.setReference(new LegalReference("law","测试法规","12","repealed",null,null,null,null,"fid"));when(metadata.enrich(anyList())).thenAnswer(i->i.getArgument(0));}
    @AfterEach void close(){service.close();}
    @Test void missingFulltextUsesParameterizedLiteralTermsAndMetadataFiltersWithoutVectorCall(){
        try(var login=mockStatic(LoginHelper.class)){login.when(LoginHelper::getTenantId).thenReturn("000000");
            var result=service.search(List.of(1L),FactElements.fallback("借款未还"),true,new FactSearchKnowledge.Filter("law","repealed"));assertEquals(1,result.hits().size());assertEquals(1,result.attemptedLibraries());verifyNoInteractions(retrieval);
            assertTrue(sql.get(1).contains("LOCATE(?,f.content)"));assertTrue(sql.get(1).contains("a.validity_status"));assertTrue(sql.get(1).contains("f.tenant_id=?"));assertEquals("000000",bindings.get(1)[0]);assertEquals(1L,bindings.get(1)[1]);assertFalse(sql.get(1).contains("借款未还"));
        }
    }
    @Test void hybridReceivesServerPrincipalAndPreservesActualMetadata(){
        var library=new KnowledgeInfoVo();library.setEmbeddingModel("embed");library.setVectorModel("milvus");when(info.queryById(1L)).thenReturn(library);when(retrieval.retrieve(any())).thenReturn(List.of(hit));
        try(var login=mockStatic(LoginHelper.class)){login.when(LoginHelper::getTenantId).thenReturn("000000");login.when(LoginHelper::getUserId).thenReturn(7L);
            var result=service.search(List.of(1L),FactElements.fallback("借款"),false,new FactSearchKnowledge.Filter("all","all"));assertEquals(1,result.hits().size());assertEquals("repealed",result.hits().get(0).getReference().status());
            verify(retrieval).retrieve(argThat(q->q.getEnableHybrid()&&q.getRetrievalPrincipal().userId()==7L&&q.getQuery().contains("借款")));
        }
    }
    @Test void inaccessibleScopeAndTooManyLibrariesRejectedAndEmptyScopeDoesNotQuery(){
        assertThrows(ServiceException.class,()->service.scope(List.of(2L)));assertThrows(ServiceException.class,()->service.scope(List.of(1L,1L,1L,1L,1L)));
        assertTrue(service.search(List.of(),FactElements.fallback("合同"),false,new FactSearchKnowledge.Filter("all","all")).hits().isEmpty());assertTrue(sql.isEmpty());
    }
    @Test void mismatchedReferenceTypeDoesNotPassMetadataFilter(){assertFalse(new FactSearchKnowledge.Filter("case","all").accepts(hit));assertFalse(new FactSearchKnowledge.Filter("law","effective").accepts(hit));}
    @Test void fulltextHitsDoNotSkipSpecificLexicalRecallAndBothQueriesStayBounded(){
        var statements=new ArrayList<String>();
        JdbcTemplate available=new JdbcTemplate(){@Override public <T> List<T> query(String statement,RowMapper<T> mapper,Object... args){statements.add(statement);return (List<T>)List.of(hit);}};
        var current=new FactSearchKnowledge(access,info,retrieval,metadata,available);
        try(var login=mockStatic(LoginHelper.class)){
            login.when(LoginHelper::getTenantId).thenReturn("000000");
            current.search(List.of(1L),FactElements.fallback("借款到期未还"),true,new FactSearchKnowledge.Filter("all","all"));
            assertEquals(2,statements.size());assertTrue(statements.stream().allMatch(s->s.contains("LIMIT 120")));
            assertTrue(statements.get(1).contains("ORDER BY ((LOCATE(?,f.content)>0)"));
        }finally{current.close();}
    }
}
