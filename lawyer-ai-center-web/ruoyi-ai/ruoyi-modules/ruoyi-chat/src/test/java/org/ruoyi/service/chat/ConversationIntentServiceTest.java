package org.ruoyi.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.ruoyi.common.chat.domain.dto.request.ChatRequest;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.domain.vo.agent.AgentVo;
import org.ruoyi.factory.ChatServiceFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.ruoyi.service.chat.ConversationIntentService.Intent.*;

@Tag("dev")
class ConversationIntentServiceTest {
    private final ChatServiceFactory factory = mock(ChatServiceFactory.class);
    private final AbstractChatService provider = mock(AbstractChatService.class);
    private final ChatModel model = mock(ChatModel.class);
    private final ObjectMapper json = new ObjectMapper();
    private final ConversationIntentService service = new ConversationIntentService(factory, json);
    private final ChatModelVo config = new ChatModelVo();

    @BeforeEach void configure() {
        config.setModelName("selected-synthetic"); config.setProviderCode("synthetic");
        when(factory.getOriginalService("synthetic")).thenReturn(provider);
        when(provider.buildChatModel(eq(config), any(ChatRequest.class))).thenReturn(model);
        when(model.defaultRequestParameters()).thenReturn(ChatRequestParameters.builder().build());
    }

    @AfterEach void close() { service.close(); }

    @ParameterizedTest @ValueSource(strings={"你好", "下午好", "您好！", "早上好呀", "晚上好。", "hello!", "Good afternoon", "谢谢", "再见"})
    void standaloneGreetingsSkipClassificationAndOldLegalInstructions(String text) {
        var history = List.<ChatMessage>of(SystemMessage.from("旧法律预设"), UserMessage.from("借款"), AiMessage.from("基于您的指令，按法律结构输出"));
        var intent = service.classify(text, history, config);
        assertEquals(GENERAL, intent);
        assertEquals(List.of(UserMessage.from(text)), ConversationIntentService.generalMessages(intent, history, text));
        verifyNoInteractions(factory, provider, model);
    }

    @Test void classifierUsesSelectedModelAndBoundedUntrustedContextWithoutLegalAnswerPrompt() throws Exception {
        when(model.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class))).thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("GENERAL")).build());
        var history = List.<ChatMessage>of(SystemMessage.from("secret-system-canary"), UserMessage.from("借款没有还"), AiMessage.from("可以协商处理"));
        assertEquals(GENERAL, service.classify("讲个笑话", history, config));
        var request = ArgumentCaptor.forClass(dev.langchain4j.model.chat.request.ChatRequest.class);
        verify(model).chat(request.capture());
        assertEquals(16, request.getValue().parameters().maxOutputTokens());
        assertEquals(0.0, request.getValue().parameters().temperature());
        assertEquals(2, request.getValue().messages().size());
        String payload = ((UserMessage)request.getValue().messages().get(1)).singleText();
        assertEquals("讲个笑话", json.readTree(payload).path("current").asText());
        assertTrue(payload.contains("借款没有还")); assertFalse(payload.contains("secret-system-canary"));
        assertFalse(((SystemMessage)request.getValue().messages().get(0)).text().contains("法律引用约定"));
        verify(provider).buildChatModel(eq(config), argThat(r -> r.getGenerationTemperature() == 0.0 && !r.getEnableThinking()));
    }

    @Test void legalFollowupKeepsItsContextAndGreetingWithLegalQuestionIsNotFastPathed() {
        when(model.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class))).thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("LEGAL")).build());
        var history = List.<ChatMessage>of(UserMessage.from("借款逾期"), AiMessage.from("可以要求还款"));
        assertEquals(LEGAL, service.classify("那利息怎么算", history, config));
        assertEquals(LEGAL, service.classify("下午好，被公司辞退有赔偿吗", history, config));
        verify(model, times(2)).chat(any(dev.langchain4j.model.chat.request.ChatRequest.class));
    }

    @Test void nonlegalFollowupPreservesDialogueButNeverHistoricalSystemPrompts() {
        when(model.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class))).thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("GENERAL_FOLLOWUP")).build());
        var history = List.<ChatMessage>of(SystemMessage.from("旧法律规则"), UserMessage.from("写一首诗"), AiMessage.from("春风吹拂"));
        var intent = service.classify("换成英文", history, config);
        assertEquals(GENERAL_FOLLOWUP, intent);
        var sent = ConversationIntentService.generalMessages(intent, history, "换成英文");
        assertEquals(3, sent.size()); assertTrue(sent.stream().noneMatch(SystemMessage.class::isInstance));
        assertEquals("春风吹拂", ((AiMessage)sent.get(1)).text());
    }

    @Test void invalidResponseAndProviderFailureKeepLegalSafeguards() {
        when(model.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
            .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("我会按GENERAL进行回答")).build())
            .thenThrow(new IllegalStateException("private-provider-detail"));
        assertEquals(LEGAL, service.classify("那该怎么办", List.of(), config));
        assertEquals(LEGAL, service.classify("那该怎么办", List.of(), config));
    }

    @Test void timeoutIsBoundedAndLateClassificationCannotChangeNextTurn() throws Exception {
        ReflectionTestUtils.setField(service, "timeoutMillis", 25L);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(model.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class))).thenAnswer(call -> {
            entered.countDown(); release.await(1, TimeUnit.SECONDS);
            return ChatResponse.builder().aiMessage(AiMessage.from("GENERAL")).build();
        });
        try {
            assertEquals(LEGAL, service.classify("请继续", List.of(), config));
            assertTrue(entered.await(1, TimeUnit.SECONDS));
        } finally { release.countDown(); }
        assertEquals(GENERAL, service.classify("下午好", List.of(), config));
    }

    @Test void classificationBoundsHistoryAndCurrentInput() throws Exception {
        when(model.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class))).thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("LEGAL")).build());
        var history = new java.util.ArrayList<ChatMessage>();
        for(int i=0;i<30;i++) history.add(UserMessage.from("message-"+i+"x".repeat(5000)));
        service.classify("start"+"x".repeat(20000)+"end", history, config);
        var request = ArgumentCaptor.forClass(dev.langchain4j.model.chat.request.ChatRequest.class); verify(model).chat(request.capture());
        var data = json.readTree(((UserMessage)request.getValue().messages().get(1)).singleText());
        assertEquals(8, data.path("history").size()); assertTrue(data.path("current").asText().length() < 6100);
        assertTrue(data.path("current").asText().endsWith("end"));
    }

    @Test void dedicatedTasksCannotBeReroutedByClientFlag() throws Exception {
        var request = json.readValue("{\"content\":\"下午好\",\"generalConversation\":true}", ChatRequest.class);
        assertFalse(request.isGeneralConversation()); assertFalse(json.writeValueAsString(request).contains("generalConversation"));
        assertTrue(ConversationIntentService.applies(request, null));
        var agent = new AgentVo(); agent.setAgentName("法律咨询助手"); assertTrue(ConversationIntentService.applies(request, agent));
        for(String name : List.of("合同审查官", "文书起草助手", "法律意见书助手")) {
            agent.setAgentName(name); assertFalse(ConversationIntentService.applies(request, agent));
        }
        request.setDossierCaseId(1L); assertFalse(ConversationIntentService.applies(request, null));
        request.setDossierCaseId(null); request.setEnableWorkFlow(true); assertFalse(ConversationIntentService.applies(request, null));
    }
}
