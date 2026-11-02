package org.ruoyi.websocket.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.ruoyi.common.chat.domain.dto.request.ChatRequest;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.ruoyi.factory.ChatServiceFactory;
import org.ruoyi.service.agent.IAgentService;
import org.ruoyi.service.chat.*;
import org.ruoyi.service.knowledge.IKnowledgeInfoService;
import org.ruoyi.service.retrieval.KnowledgeRetrievalService;
import org.springframework.web.socket.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.ruoyi.service.chat.ConversationIntentService.Intent.*;

@Tag("dev")
class MpConversationIntentTest {
    private final IChatModelService models = mock(IChatModelService.class);
    private final IAgentService agents = mock(IAgentService.class);
    private final ChatServiceFactory factory = mock(ChatServiceFactory.class);
    private final AbstractChatService provider = mock(AbstractChatService.class);
    private final StreamingChatModel stream = mock(StreamingChatModel.class);
    private final IChatMessageService messages = mock(IChatMessageService.class);
    private final ChatSessionOwnershipGuard ownership = mock(ChatSessionOwnershipGuard.class);
    private final IKnowledgeInfoService knowledge = mock(IKnowledgeInfoService.class);
    private final KnowledgeRetrievalService retrieval = mock(KnowledgeRetrievalService.class);
    private final LawyerUsageService usage = mock(LawyerUsageService.class);
    private final ConversationIntentService intents = mock(ConversationIntentService.class);
    private final WebSocketSession socket = mock(WebSocketSession.class);
    private final MpChatWebSocketHandler handler = new MpChatWebSocketHandler(factory,models,agents,knowledge,retrieval,messages,ownership,new ObjectMapper(),new ConsultationModelResolver(models));
    private final ChatModelVo model = ConsultationModelResolverTest.model(90071992547409937L,"chat");

    @BeforeEach void prepare() {
        var principal = ConsultationModelResolverTest.principal("system:model:list");
        when(socket.getAttributes()).thenReturn(new HashMap<>(Map.of(MpChatHandshakeInterceptor.USER_ID_KEY,7L,MpChatHandshakeInterceptor.LOGIN_USER_KEY,principal)));
        when(socket.isOpen()).thenReturn(true); handler.setUsage(usage); handler.setConversationIntentService(intents);
        var agent = ConsultationModelResolverTest.agent("法律咨询助手"); agent.setKnowledgeIds(List.of(12L)); agent.setSystemPrompt("法律预设提示词");
        when(agents.queryById(2L)).thenReturn(agent); when(models.queryAvailableList(any())).thenReturn(List.of(model));
        when(models.queryById(11L)).thenReturn(model);
        when(messages.getMessagesBySessionId(21L)).thenReturn(List.of(UserMessage.from("借款逾期"),AiMessage.from("按法律结构回答")));
        when(factory.getOriginalService("synthetic")).thenReturn(provider); when(provider.buildStreamingChatModel(eq(model),any(ChatRequest.class))).thenReturn(stream);
    }

    private void send(String text) throws Exception {
        handler.handleTextMessage(socket,new TextMessage(new ObjectMapper().writeValueAsString(Map.of(
            "content",text,"sessionId","21","agentId","2","knowledgeId","13",
            "systemPrompt","客户端旧法律预设","consultationModelId","90071992547409937"))));
    }

    @Test @SuppressWarnings("unchecked")
    void mobileGreetingSkipsPresetAndRetrievalButKeepsExactSelectedModelAndHistoryPersistence() throws Exception {
        when(intents.classify(eq("下午好"),anyList(),same(model))).thenReturn(GENERAL);
        send("下午好");
        var sent=ArgumentCaptor.forClass(List.class); var callback=ArgumentCaptor.forClass(StreamingChatResponseHandler.class);
        verify(stream,timeout(2000)).chat(sent.capture(),callback.capture());
        assertEquals(List.of(UserMessage.from("下午好")),sent.getValue());
        verify(provider).buildStreamingChatModel(same(model),argThat(ChatRequest::isGeneralConversation));
        verifyNoInteractions(knowledge,retrieval); verify(ownership).requireOwned(7L,21L);
        verify(usage).beginProcessing("synthetic",7L,21L); verify(messages).saveChatMessage(7L,21L,"下午好","user",model.getModelName());
        callback.getValue().onPartialResponse("下午好！"); callback.getValue().onCompleteResponse(null);
        verify(messages).saveChatMessage(7L,21L,"下午好！","assistant",model.getModelName());
    }

    @Test @SuppressWarnings("unchecked")
    void legalFollowupStillGetsLegalRulesHistoryAndKnowledge() throws Exception {
        when(intents.classify(anyString(),anyList(),same(model))).thenReturn(LEGAL);
        send("那利息怎么算");
        var sent=ArgumentCaptor.forClass(List.class); verify(stream,timeout(2000)).chat(sent.capture(),any(StreamingChatResponseHandler.class));
        var legal=(List<ChatMessage>)sent.getValue(); assertInstanceOf(SystemMessage.class,legal.get(0));
        assertTrue(((SystemMessage)legal.get(0)).text().contains("法律预设提示词"));
        assertTrue(((SystemMessage)legal.get(0)).text().contains("法律引用约定"));
        assertTrue(legal.contains(UserMessage.from("借款逾期"))); assertEquals(UserMessage.from("那利息怎么算"),legal.get(legal.size()-1));
        verify(knowledge).queryByIdFor(12L,7L,"synthetic"); verify(knowledge).queryByIdFor(13L,7L,"synthetic");
    }

    @Test void unavailableSessionCannotClassifyOrCallModel() throws Exception {
        doThrow(new IllegalArgumentException("不可用")).when(ownership).requireOwned(7L,21L);
        send("下午好"); verifyNoInteractions(intents,usage,factory,knowledge,retrieval);
    }
}
