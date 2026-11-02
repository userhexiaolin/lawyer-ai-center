package org.ruoyi.service.chat.impl;

import cn.dev33.satoken.stp.StpUtil;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.ruoyi.common.chat.domain.dto.request.ChatRequest;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.sse.core.SseEmitterManager;
import org.ruoyi.domain.vo.agent.AgentVo;
import org.ruoyi.factory.ChatServiceFactory;
import org.ruoyi.service.agent.IAgentService;
import org.ruoyi.service.chat.*;
import org.ruoyi.service.knowledge.IKnowledgeInfoService;
import org.ruoyi.service.retrieval.KnowledgeRetrievalService;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.ruoyi.service.chat.ConversationIntentService.Intent.*;

@Tag("dev")
class ChatConversationIntentTest {
    private final IChatModelService models = mock(IChatModelService.class);
    private final ChatServiceFactory factory = mock(ChatServiceFactory.class);
    private final AbstractChatService provider = mock(AbstractChatService.class);
    private final StreamingChatModel stream = mock(StreamingChatModel.class);
    private final IKnowledgeInfoService knowledge = mock(IKnowledgeInfoService.class);
    private final KnowledgeRetrievalService retrieval = mock(KnowledgeRetrievalService.class);
    private final SseEmitterManager emitters = mock(SseEmitterManager.class);
    private final IChatMessageService messages = mock(IChatMessageService.class);
    private final ChatSessionOwnershipGuard ownership = mock(ChatSessionOwnershipGuard.class);
    private final IAgentService agents = mock(IAgentService.class);
    private final ConversationIntentService intents = mock(ConversationIntentService.class);
    private final LawyerUsageService usage = mock(LawyerUsageService.class);
    private final ChatServiceFacade facade = new ChatServiceFacade(models,factory,knowledge,retrieval,emitters,messages,ownership,null,agents,null,null,mock(org.ruoyi.common.trace.config.TraceProperties.class));
    private final ChatModelVo model = new ChatModelVo();
    private final AgentVo agent = new AgentVo();

    @BeforeEach void prepare() {
        ReflectionTestUtils.setField(facade, "conversationIntentService", intents); facade.setUsage(usage);
        model.setModelName("chosen-model"); model.setProviderCode("synthetic");
        agent.setId(2L); agent.setAgentName("法律咨询助手"); agent.setStatus("0"); agent.setModelId(11L);
        agent.setSystemPrompt("法律预设：按结论摘要、法律分析回答"); agent.setKnowledgeIds(List.of(12L)); agent.setMcpToolIds(List.of(99L));
        when(agents.queryById(2L)).thenReturn(agent); when(models.queryById(11L)).thenReturn(model);
        when(models.selectModelByName("chosen-model")).thenReturn(model);
        when(factory.getOriginalService("synthetic")).thenReturn(provider); when(provider.buildStreamingChatModel(eq(model),any())).thenReturn(stream);
        when(emitters.connect(anyString())).thenReturn(new SseEmitter());
    }

    private ChatRequest request(long id, String content) {
        ChatRequest request = new ChatRequest(); request.setSessionId(id); request.setAgentId(2L); request.setContent(content); request.setKnowledgeId("13"); return request;
    }

    private void send(ChatRequest request) {
        try (var login = mockStatic(LoginHelper.class); var token = mockStatic(StpUtil.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L); login.when(LoginHelper::getTenantId).thenReturn("synthetic");
            token.when(StpUtil::getTokenValue).thenReturn("synthetic-token");
            assertNotNull(facade.sseChat(request));
        }
    }

    @Test @SuppressWarnings("unchecked")
    void webChatGreetingSkipsLegalPromptKnowledgeAndSupervisorWithoutChangingModelOrPersistence() {
        var request = request(81001, "下午好");
        when(messages.getMessagesBySessionId(81001L)).thenReturn(List.of(UserMessage.from("借款问题"),AiMessage.from("基于您的指令，严格按法律结构回答")));
        when(intents.classify(eq("下午好"),anyList(),same(model))).thenReturn(GENERAL);
        send(request);
        var sent = ArgumentCaptor.forClass(List.class); verify(stream).chat(sent.capture(),any(StreamingChatResponseHandler.class));
        assertEquals(List.of(UserMessage.from("下午好")),sent.getValue()); assertTrue(request.isGeneralConversation());
        assertNull(request.getGenerationTemperature()); assertEquals("chosen-model",request.getModel());
        verifyNoInteractions(knowledge,retrieval); verify(provider,never()).buildChatModel(any());
        verify(ownership).requireOwned(7L,81001L); verify(usage).beginProcessing("synthetic",7L,81001L);
        verify(messages).saveChatMessage(7L,81001L,"下午好","user","chosen-model");
    }

    @Test @SuppressWarnings("unchecked")
    void legalFollowupInSameConversationRestoresLegalPromptAndRetrieval() {
        agent.setMcpToolIds(List.of());
        when(messages.getMessagesBySessionId(81002L)).thenReturn(List.of(UserMessage.from("借款到期未还"),AiMessage.from("可以要求返还借款")));
        when(intents.classify(anyString(),anyList(),same(model))).thenReturn(GENERAL,LEGAL);
        send(request(81002,"下午好"));
        var followup = request(81002,"那利息怎么算"); followup.setGeneralConversation(true);
        send(followup);
        var sent = ArgumentCaptor.forClass(List.class); verify(stream,times(2)).chat(sent.capture(),any(StreamingChatResponseHandler.class));
        var legal = (List<ChatMessage>)sent.getAllValues().get(1);
        assertFalse(followup.isGeneralConversation()); assertInstanceOf(SystemMessage.class,legal.get(0));
        assertTrue(((SystemMessage)legal.get(0)).text().contains("法律引用约定"));
        assertTrue(legal.contains(UserMessage.from("借款到期未还")));
        assertEquals(1,legal.stream().filter(m -> m instanceof UserMessage u && u.singleText().equals("那利息怎么算")).count());
        verify(knowledge).queryById(12L); verify(knowledge,never()).queryById(13L);
        assertEquals(0.1,followup.getGenerationTemperature());
    }

    @Test @SuppressWarnings("unchecked")
    void plainModelChatAlsoUsesIntentAndNonlegalFollowupRetainsContext() {
        var request = request(81003,"换成英文"); request.setAgentId(null); request.setModel("chosen-model");
        when(messages.getMessagesBySessionId(81003L)).thenReturn(List.of(UserMessage.from("写首诗"),AiMessage.from("春风吹拂")));
        when(intents.classify(anyString(),anyList(),same(model))).thenReturn(GENERAL_FOLLOWUP);
        send(request);
        var sent = ArgumentCaptor.forClass(List.class); verify(stream).chat(sent.capture(),any(StreamingChatResponseHandler.class));
        assertEquals(List.of(UserMessage.from("写首诗"),AiMessage.from("春风吹拂"),UserMessage.from("换成英文")),sent.getValue());
        verifyNoInteractions(knowledge,retrieval,agents);
    }

    @Test void unavailableSessionCannotReachClassifierOrModel() {
        doThrow(new IllegalArgumentException("对话会话不可用")).when(ownership).requireOwned(7L,81004L);
        try (var login=mockStatic(LoginHelper.class); var token=mockStatic(StpUtil.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            assertThrows(IllegalArgumentException.class,()->facade.sseChat(request(81004,"下午好")));
        }
        verifyNoInteractions(intents,factory,usage,knowledge,retrieval);
    }
}
