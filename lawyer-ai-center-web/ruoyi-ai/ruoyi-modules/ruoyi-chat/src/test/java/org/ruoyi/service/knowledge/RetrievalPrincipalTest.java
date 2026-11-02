package org.ruoyi.service.knowledge;

import org.junit.jupiter.api.*;
import org.mockito.*;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.bo.vector.QueryVectorBo;
import org.ruoyi.domain.vo.knowledge.KnowledgeInfoVo;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.service.knowledge.retriever.CustomVectorRetriever;
import org.ruoyi.service.retrieval.KnowledgeRetrievalService;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class RetrievalPrincipalTest {
    @Test void scopedTenantRestoresNestedAndExceptionalWorkers(){
        var helper=org.ruoyi.common.tenant.helper.TenantHelper.class;
        try(var tenant=mockStatic(helper,CALLS_REAL_METHODS);var login=mockStatic(LoginHelper.class)){
            tenant.when(org.ruoyi.common.tenant.helper.TenantHelper::isEnable).thenReturn(true);
            assertNull(org.ruoyi.common.tenant.helper.TenantHelper.getDynamic());
            org.ruoyi.common.tenant.helper.TenantHelper.threadScope("100001",()->{
                assertEquals("100001",org.ruoyi.common.tenant.helper.TenantHelper.getDynamic());
                assertThrows(IllegalArgumentException.class,()->org.ruoyi.common.tenant.helper.TenantHelper.threadScope("100002",()->{throw new IllegalArgumentException();}));
                assertEquals("100001",org.ruoyi.common.tenant.helper.TenantHelper.getDynamic());return null;
            });
            assertNull(org.ruoyi.common.tenant.helper.TenantHelper.getDynamic());
        }
    }
    @Test void clientJsonCannotSupplyOrReadServerIdentity() throws Exception {
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var input=mapper.readValue("{\"kid\":\"12\",\"retrievalPrincipal\":{\"userId\":1,\"tenantId\":\"000000\"}}",QueryVectorBo.class);
        assertNull(input.getRetrievalPrincipal());
        input.setRetrievalPrincipal(new QueryVectorBo.RetrievalPrincipal(7L,"000000"));
        assertFalse(mapper.writeValueAsString(input).contains("retrievalPrincipal"));
    }
    @Test void concurrentRetrieverUsesIdentityCapturedBeforeDispatch(){
        var service=mock(KnowledgeRetrievalService.class);
        when(service.retrieve(any())).thenReturn(List.of());
        var info=new KnowledgeInfoVo();info.setId(12L);var model=new ChatModelVo();
        CustomVectorRetriever retriever;
        try(MockedStatic<LoginHelper> login=mockStatic(LoginHelper.class)){
            login.when(LoginHelper::getUserId).thenReturn(7L);
            login.when(LoginHelper::getTenantId).thenReturn("000000");
            retriever=new CustomVectorRetriever(service,info,model);
        }
        CompletableFuture.runAsync(()->retriever.retrieve(dev.langchain4j.rag.query.Query.from("合同"))).join();
        var argument=ArgumentCaptor.forClass(QueryVectorBo.class);verify(service).retrieve(argument.capture());
        assertEquals(7L,argument.getValue().getRetrievalPrincipal().userId());
        assertEquals("000000",argument.getValue().getRetrievalPrincipal().tenantId());
    }
}
