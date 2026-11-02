package org.ruoyi.websocket.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ruoyi.common.chat.domain.dto.request.ChatRequest;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.ruoyi.factory.ChatServiceFactory;
import org.ruoyi.service.agent.IAgentService;
import org.ruoyi.service.chat.AbstractChatService;
import org.ruoyi.service.chat.ChatSessionOwnershipGuard;
import org.ruoyi.service.chat.IChatMessageService;
import org.ruoyi.service.chat.LawyerUsageService;
import org.ruoyi.service.knowledge.IKnowledgeInfoService;
import org.ruoyi.service.retrieval.KnowledgeRetrievalService;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class MpConsultationModelTest {
    private final IChatModelService models=mock(IChatModelService.class);
    private final IAgentService agents=mock(IAgentService.class);
    private final ChatServiceFactory factory=mock(ChatServiceFactory.class);
    private final AbstractChatService provider=mock(AbstractChatService.class);
    private final IChatMessageService messages=mock(IChatMessageService.class);
    private final LawyerUsageService usage=mock(LawyerUsageService.class);
    private final WebSocketSession socket=mock(WebSocketSession.class);
    private final MpChatWebSocketHandler handler=new MpChatWebSocketHandler(factory,models,agents,mock(IKnowledgeInfoService.class),mock(KnowledgeRetrievalService.class),messages,mock(ChatSessionOwnershipGuard.class),new ObjectMapper(),new ConsultationModelResolver(models));
    private final ChatModelVo configured=ConsultationModelResolverTest.model(11L,"chat"),selected=ConsultationModelResolverTest.model(90071992547409937L,"chat");
    private void prepare(String agentName) {
        var principal=ConsultationModelResolverTest.principal("system:model:list");
        when(socket.getAttributes()).thenReturn(new HashMap<>(Map.of(MpChatHandshakeInterceptor.USER_ID_KEY,7L,MpChatHandshakeInterceptor.LOGIN_USER_KEY,principal)));
        when(socket.isOpen()).thenReturn(true);handler.setUsage(usage);
        when(agents.queryById(2L)).thenReturn(ConsultationModelResolverTest.agent(agentName));
        when(models.queryById(11L)).thenReturn(configured);when(models.queryAvailableList(any())).thenReturn(List.of(selected));
        when(messages.getMessagesBySessionId(21L)).thenReturn(List.of());
        when(factory.getOriginalService("synthetic")).thenReturn(provider);when(provider.buildStreamingChatModel(any(),any())).thenReturn(mock(StreamingChatModel.class));
    }
    private void send(String extra) throws Exception { handler.handleTextMessage(socket,new TextMessage("{\"content\":\"合成咨询问题\",\"sessionId\":\"21\",\"agentId\":\"2\""+extra+"}")); }
    @Test void actualHandlerBuildsSelectedModelInsteadOfAgentDefault() throws Exception {
        prepare("法律咨询助手");send(",\"consultationModelId\":\"90071992547409937\"");
        verify(provider).buildStreamingChatModel(same(selected),argThat(request->selected.getModelName().equals(request.getModel())));verify(models,never()).queryById(11L);
        verify(usage).beginProcessing("synthetic",7L,21L);
    }
    @Test void legacyAndExplicitDefaultKeepAgentPriority() throws Exception {
        prepare("法律咨询助手");send(",\"model\":\"ignored-legacy-fallback\",\"consultationModelId\":\"\"");
        verify(provider).buildStreamingChatModel(same(configured),any(ChatRequest.class));verify(models,never()).queryAvailableList(any());
    }
    @Test void invalidChoiceCannotStartUsageOrInvokeAnotherModel() throws Exception {
        prepare("法律咨询助手");send(",\"consultationModelId\":\"22\"");
        verifyNoInteractions(usage,factory);verify(messages,never()).saveChatMessage(any(),any(),any(),any(),any());
    }
    @Test void reviewCannotUseConsultationOverride() throws Exception {
        prepare("合同审查官");send(",\"consultationModelId\":\"90071992547409937\"");verifyNoInteractions(usage,factory);verify(models,never()).queryAvailableList(any());
    }
}
