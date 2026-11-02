package org.ruoyi.service.legal;

import org.junit.jupiter.api.*;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.vo.chat.ChatSessionVo;
import org.ruoyi.domain.vo.knowledge.*;
import org.ruoyi.service.chat.*;
import org.ruoyi.service.knowledge.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class FactSearchServiceTest {
    FactSearchModel model=mock(FactSearchModel.class);FactSearchKnowledge knowledge=mock(FactSearchKnowledge.class);KnowledgeAccess access=mock(KnowledgeAccess.class);
    ChatSessionOwnershipGuard guard=mock(ChatSessionOwnershipGuard.class);IChatSessionService sessions=mock(IChatSessionService.class);LawyerUsageService usage=mock(LawyerUsageService.class);IChatMessageService messages=mock(IChatMessageService.class);
    FactSearchService service=new FactSearchService(model,knowledge,access,mock(IKnowledgeInfoService.class),guard,sessions,usage,messages);
    FactSearchService.Request request=new FactSearchService.Request(12L,"张某出借30000元，有借条和转账，约定还款日已过仍未归还。",null,"all","all");
    @BeforeEach void setup() {var session=new ChatSessionVo();session.setSessionTitle("案情检索 - 借款");when(sessions.queryById(12L)).thenReturn(session);when(knowledge.scope(null)).thenReturn(List.of(1L));when(access.readableIds()).thenReturn(List.of(1L));}
    @Test void extractionFailureStillSearchesAndSharesSingleExistingQuotaCharge() throws Exception {
        when(model.extract(any())).thenThrow(new IllegalArgumentException("bad JSON"));when(knowledge.search(any(),any(),eq(true),any())).thenReturn(new FactSearchKnowledge.Result(List.of(),1,true,List.of()));
        try(var login=mockStatic(LoginHelper.class)){login.when(LoginHelper::getUserId).thenReturn(7L);login.when(LoginHelper::getTenantId).thenReturn("000000");
            var result=service.search(request);assertEquals("fallback",result.extractionStatus());assertEquals(1,result.attemptedLibraries());assertFalse(result.elements().keywords().isEmpty());assertTrue(result.message().contains("知识库暂无匹配"));
            verify(usage,times(1)).beginProcessing("000000",7L,12L);verifyNoMoreInteractions(usage);verify(knowledge).search(eq(List.of(1L)),any(),eq(true),any());
            verify(messages,times(2)).saveChatMessage(eq(7L),eq(12L),anyString(),anyString(),eq("案情检索"));
        }
    }
    @Test void returnsRealMetadataAndConstructsSearchFromKeywordsCauseAndLawHints() throws Exception {
        var elements=new FactElements(List.of("民间借贷"),List.of("借款","还款"),List.of("利息约定"),List.of("张某"),List.of("用户指定法规"));when(model.extract(any())).thenReturn(elements);
        var hit=new KnowledgeRetrievalVo();hit.setId("fid-1");hit.setKnowledgeId(1L);hit.setContent("真实借款条文片段");hit.setReference(new LegalReference("law","测试法规","12","repealed",null,null,null,null,"fid-1"));
        when(knowledge.search(any(),eq(elements),eq(false),any())).thenReturn(new FactSearchKnowledge.Result(List.of(hit),1,false,List.of()));
        try(var login=mockStatic(LoginHelper.class)){login.when(LoginHelper::getUserId).thenReturn(7L);login.when(LoginHelper::getTenantId).thenReturn("000000");
            var result=service.search(request);assertEquals("hybrid",result.retrievalMode());assertTrue(result.query().contains("借款 还款 民间借贷 用户指定法规"));assertEquals("repealed",result.hits().get(0).reference().status());assertEquals("fid-1",result.hits().get(0).reference().fragmentId());
            verify(messages).saveChatMessage(eq(7L),eq(12L),contains("legal-citations"),eq("assistant"),eq("案情检索"));
        }
    }
    @Test void foreignSessionAndInaccessibleLibraryAreRejectedBeforeChargingOrExtracting() throws Exception {
        try(var login=mockStatic(LoginHelper.class)){login.when(LoginHelper::getUserId).thenReturn(7L);doThrow(new IllegalArgumentException()).when(guard).requireOwned(7L,12L);
            assertThrows(ServiceException.class,()->service.search(request));verifyNoInteractions(model,usage,knowledge,messages);
            reset(guard);when(knowledge.scope(null)).thenThrow(new ServiceException("forbidden",403));assertThrows(ServiceException.class,()->service.search(request));verifyNoInteractions(model,usage,messages);
        }
    }
    @Test void rejectsInvalidInputAndMetadataFiltersBeforeModelCall() {
        assertThrows(ServiceException.class,()->service.search(new FactSearchService.Request(12L,"",null,"all","all")));
        assertThrows(ServiceException.class,()->service.search(new FactSearchService.Request(12L,"借款",null,"external_case","all")));verifyNoInteractions(model,usage,guard);
    }
    @Test void strictJsonAndFallbackNeverConstructCitationOrCaseNumber() throws Exception {
        String valid="{\"causeHints\":[\"民间借贷\"],\"keywords\":[\"借款\"],\"disputes\":[],\"parties\":[\"张某\"],\"lawsHints\":[]}";
        assertEquals(List.of("借款"),FactElements.parse(valid).keywords());
        for(String bad:List.of("null","{}",valid+"{}",valid.replace("[\"借款\"]","\"借款\"")))assertThrows(Exception.class,()->FactElements.parse(bad));
        var fallback=FactElements.fallback(request.text());assertTrue(fallback.causeHints().isEmpty());assertTrue(fallback.lawsHints().isEmpty());assertTrue(FactElements.PROMPT.contains("不编造案号"));
        var grounded=new FactElements(List.of("民间借贷"),List.of("借款"),List.of(),List.of("张某","虚构当事人"),List.of("虚构法规")).groundedIn(request.text());
        assertEquals(List.of("张某"),grounded.parties());assertTrue(grounded.lawsHints().isEmpty());
        var invented=new FactElements(List.of(),List.of("（2025）赣0102民初999号"),List.of(),List.of(),List.of());
        assertThrows(IllegalArgumentException.class,()->invented.groundedIn(request.text()));
        assertEquals(invented, invented.groundedIn("参考（2025）赣0102民初999号"));
    }
    @Test void limitedScopeAndEmptyResultsExplainCoverageWithoutChangingQuotaOrScope() throws Exception {
        when(model.extract(any())).thenReturn(FactElements.fallback("借款"));
        when(access.readableIds()).thenReturn(List.of(1L,2L,3L,4L,5L,6L));
        when(knowledge.scope(null)).thenReturn(List.of(1L,2L,3L,4L));
        when(knowledge.search(any(),any(),eq(false),any())).thenReturn(new FactSearchKnowledge.Result(List.of(),4,false,List.of()));
        try(var login=mockStatic(LoginHelper.class)){
            login.when(LoginHelper::getUserId).thenReturn(7L);login.when(LoginHelper::getTenantId).thenReturn("000000");
            var result=service.search(request);
            assertTrue(result.warnings().stream().anyMatch(w->w.contains("6个知识库")&&w.contains("前4个")&&w.contains("未覆盖")));
            assertTrue(result.message().contains("不代表不存在相关法规或案例"));
            verify(usage,times(1)).beginProcessing("000000",7L,12L);
        }
    }
}
