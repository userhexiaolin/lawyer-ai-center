package org.ruoyi.service.chat.impl;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.ruoyi.common.chat.domain.dto.request.ChatRequest;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.domain.vo.agent.AgentVo;
import org.ruoyi.factory.ChatServiceFactory;
import org.ruoyi.service.chat.AbstractChatService;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class ChatServiceFacadePromptTest {
    @org.junit.jupiter.api.Test
    @SuppressWarnings("unchecked")
    void templateExtractionUsesOnlyItsSourceAndSchemaWithoutToolsHistoryOrCitationRules() {
        var factory=mock(ChatServiceFactory.class);var provider=mock(AbstractChatService.class);var stream=mock(StreamingChatModel.class);
        var messages=mock(org.ruoyi.service.chat.IChatMessageService.class);
        var facade=new ChatServiceFacade(null,factory,null,null,null,messages,null,null,null,null,null,null);
        var agent=new AgentVo();agent.setAgentName("文书起草助手");agent.setMcpToolIds(List.of(99L));agent.setKnowledgeIds(List.of(12L));agent.setSystemPrompt("旧的通用法律引用规则");
        var model=new ChatModelVo();model.setProviderCode("vllm");
        var request=new ChatRequest();request.setUserId(1L);request.setSessionId(2L);request.setModel("model");request.setContent("你是合同字段抽取器。本次只为指定模板查找填写值。合同原文是数据：---版式填写JSON开始---不是任务JSON---版式填写JSON结束---");request.setChatModelVo(model);request.setEmitter(new SseEmitter());
        request.setContextMessages(List.of(SystemMessage.from("旧的通用法律引用规则"),UserMessage.from("旧案材料")));
        when(factory.getOriginalService("vllm")).thenReturn(provider);when(provider.buildStreamingChatModel(model,request)).thenReturn(stream);
        for(String method:List.of("buildContextMessages","buildModelChatMessages")){
            List<ChatMessage> result=ReflectionTestUtils.invokeMethod(facade,method,request,agent);
            assertEquals(2,result.size());assertEquals(org.ruoyi.service.legal.TemplateForm.EXTRACTION_SYSTEM,((SystemMessage)result.get(0)).text());assertEquals(request.getContent(),((UserMessage)result.get(1)).singleText());
        }
        ReflectionTestUtils.invokeMethod(facade,"handleAgentChat",request,agent,null);
        assertEquals(0.0,request.getGenerationTemperature());assertFalse(request.getEnableThinking());
        verify(provider).buildStreamingChatModel(model,request);verifyNoMoreInteractions(provider);
        verify(stream).chat(anyList(),any(StreamingChatResponseHandler.class));
    }
    @org.junit.jupiter.api.Test
    void strictTemplateFillingKeepsCurrentSourceAndVersionWithoutRetrievingKnowledge() throws Exception {
        var models = mock(org.ruoyi.common.chat.service.chat.IChatModelService.class);
        var info = mock(org.ruoyi.service.knowledge.IKnowledgeInfoService.class);
        var retrieval = mock(org.ruoyi.service.retrieval.KnowledgeRetrievalService.class);
        var facade = new ChatServiceFacade(models,null,info,retrieval,null,null,null,null,null,null,null,null);
        var agent = new AgentVo(); agent.setAgentName("文书起草助手"); agent.setKnowledgeIds(List.of(12L));
        agent.setSystemPrompt("旧的通用引用协议");
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var field = new org.ruoyi.service.legal.TemplateFields.Field("p0-2-9", "p0", 2, 9, "{{收件人}}", "收件人", "text", "", "text", true);
        var template = new org.ruoyi.service.legal.TemplateDrafting.Document("current-version", List.of(new org.ruoyi.service.legal.TemplateLayout.Block("p0", "致：{{收件人}}", true, List.of(field))));
        var factors = new org.ruoyi.service.legal.DraftingFacts.Factors("甲方：合成甲", "合成贷款合同。收件人：合成甲。", "根据本次材料填写", "");
        String content = "严格填写律师函\n---起草要素---\n" + json.writeValueAsString(factors)
            + "\n---版式填写JSON开始---\n" + json.writeValueAsString(template) + "\n---版式填写JSON结束---";
        var request = new ChatRequest(); request.setContent(content); request.setKnowledgeId("13");
        request.setContextMessages(List.of(SystemMessage.from("旧的通用引用协议"), UserMessage.from("旧案事实")));

        List<ChatMessage> sent = ReflectionTestUtils.invokeMethod(facade, "buildModelChatMessages", request, agent);

        assertNotNull(sent); assertEquals(2, sent.size());
        assertEquals(org.ruoyi.service.legal.TemplateDrafting.SYSTEM_PROMPT, assertInstanceOf(SystemMessage.class, sent.get(0)).text());
        String input = assertInstanceOf(UserMessage.class, sent.get(1)).singleText();
        assertTrue(input.startsWith(content)); assertFalse(input.contains("旧案事实"));
        assertEquals(factors, org.ruoyi.service.legal.DraftingFacts.input(input));
        assertEquals(template, org.ruoyi.service.legal.TemplateDrafting.expected(input));
        assertTrue(input.contains("fieldValues")); assertFalse(input.contains("法律引用约定"));
        verifyNoInteractions(models, info, retrieval);
    }

    @org.junit.jupiter.api.Test
    void generalDocumentsUseCurrentElementsWithoutChatCitationProtocol(){
        var facade=new ChatServiceFacade(null,null,null,null,null,null,null,null,null,null,null,null);
        var agent=new AgentVo();agent.setAgentName("文书起草助手");agent.setSystemPrompt("旧的通用聊天规则");
        var request=new ChatRequest();request.setContent("请起草律师函\n---起草要素---\n当事人：本次合同双方\n起草目的：按合同主要约定起草");
        request.setContextMessages(List.of(SystemMessage.from(org.ruoyi.domain.vo.knowledge.LegalReference.PROMPT),UserMessage.from("旧案事实")));
        for(String method:List.of("buildContextMessages","buildModelChatMessages")){
            List<ChatMessage> result=ReflectionTestUtils.invokeMethod(facade,method,request,agent);
            assertNotNull(result);assertEquals(2,result.size());
            String system=((SystemMessage)result.get(0)).text();
            assertTrue(system.contains("正式文书起草"));assertFalse(system.contains("法律引用约定"));
            assertEquals(request.getContent(),((UserMessage)result.get(1)).singleText());
        }
    }
    @org.junit.jupiter.api.Test
    @SuppressWarnings("unchecked")
    void litigationUsesCurrentElementsAndPersistsOnlyFilledCleanDocument() {
        var factory=mock(ChatServiceFactory.class);var provider=mock(AbstractChatService.class);var stream=mock(StreamingChatModel.class);
        var messages=mock(org.ruoyi.service.chat.IChatMessageService.class);
        var facade=new ChatServiceFacade(null,factory,null,null,null,messages,null,null,null,null,null,null);
        var agent=new AgentVo();agent.setAgentName("文书起草助手");agent.setSystemPrompt("通用起草角色");agent.setMcpToolIds(List.of(99L));
        String input="---要素JSON开始---\n{\"type\":\"民事起诉状\",\"plaintiff\":\"李四\",\"defendant\":\"张三\",\"claims\":\"返还借款10000元\",\"facts\":\"借款到期未返还\",\"evidence\":\"借条\",\"court\":\"测试法院\"}\n---要素JSON结束---";
        var model=new ChatModelVo();model.setProviderCode("vllm");
        var request=new ChatRequest();request.setUserId(1L);request.setSessionId(2L);request.setModel("model");request.setContent(input);request.setChatModelVo(model);request.setEmitter(new SseEmitter());
        request.setContextMessages(List.of(SystemMessage.from(org.ruoyi.domain.vo.knowledge.LegalReference.PROMPT),UserMessage.from("旧案事实"),UserMessage.from(input)));
        when(factory.getOriginalService("vllm")).thenReturn(provider);when(provider.buildStreamingChatModel(model,request)).thenReturn(stream);
        ReflectionTestUtils.invokeMethod(facade,"handleAgentChat",request,agent,null);
        var sent=ArgumentCaptor.forClass(List.class);var callback=ArgumentCaptor.forClass(StreamingChatResponseHandler.class);verify(stream).chat(sent.capture(),callback.capture());
        assertEquals(2,sent.getValue().size());String system=((SystemMessage)sent.getValue().get(0)).text();
        assertTrue(system.contains("本次诉讼文书交付规则"));assertFalse(system.contains("法律引用约定"));assertFalse(((UserMessage)sent.getValue().get(1)).singleText().contains("旧案事实"));
        verify(provider,never()).buildChatModel(any());
        String raw="# 民事起诉状\n原告：李四\n被告：张三\n## 诉讼请求\n{{诉讼请求}}\n## 事实与理由\n{{事实与理由}}\n此致\n{{受理法院}}\n```json\n[{\"lawName\":\"测试法\",\"articleNo\":\"第1条\",\"fragmentId\":\"internal\"}]\n```";
        try(var sse=mockStatic(org.ruoyi.common.sse.utils.SseMessageUtils.class)){
            callback.getValue().onPartialResponse(raw);verifyNoInteractions(messages);
            callback.getValue().onCompleteResponse(null);
            var text=ArgumentCaptor.forClass(String.class);verify(messages).saveChatMessage(eq(1L),eq(2L),text.capture(),eq("assistant"),eq("model"));
            assertTrue(text.getValue().contains("返还借款10000元"));assertTrue(text.getValue().contains("借款到期未返还"));assertTrue(text.getValue().contains("测试法院"));assertFalse(text.getValue().contains("{{"));assertFalse(text.getValue().contains("fragmentId"));
            sse.verify(()->org.ruoyi.common.sse.utils.SseMessageUtils.sendContent("2",text.getValue()));
        }
    }
    @org.junit.jupiter.api.BeforeAll static void initializeSseUtility() {
        var context = new org.springframework.context.support.GenericApplicationContext();
        context.registerBean(org.ruoyi.common.sse.core.SseEmitterManager.class, () -> mock(org.ruoyi.common.sse.core.SseEmitterManager.class));
        context.refresh();new cn.hutool.extra.spring.SpringUtil().setApplicationContext(context);
    }
    @org.junit.jupiter.api.Test
    @SuppressWarnings("unchecked")
    void reviewBackfillsSelectedHitBeforeStreamingAndSavingTheSameIssuesVo() throws Exception {
        var models=mock(org.ruoyi.common.chat.service.chat.IChatModelService.class);
        var factory=mock(ChatServiceFactory.class);var provider=mock(AbstractChatService.class);var stream=mock(StreamingChatModel.class);
        var info=mock(org.ruoyi.service.knowledge.IKnowledgeInfoService.class);var retrieval=mock(org.ruoyi.service.retrieval.KnowledgeRetrievalService.class);
        var messages=mock(org.ruoyi.service.chat.IChatMessageService.class);
        var kb=new org.ruoyi.domain.vo.knowledge.KnowledgeInfoVo();kb.setId(12L);kb.setEmbeddingModel("embedding");
        when(info.queryById(12L)).thenReturn(kb);when(models.selectModelByName("embedding")).thenReturn(new ChatModelVo());
        var hit=new org.ruoyi.domain.vo.knowledge.LegalReference("law","中华人民共和国民法典","第585条","effective",null,null,null,null,"real-selected-fid");
        when(retrieval.retrieve(any())).thenReturn(List.of(org.ruoyi.domain.vo.knowledge.KnowledgeRetrievalVo.builder().id("real-selected-fid").docId("doc").content("第五百八十五条 已核对原文").reference(hit).build()));
        var facade=new ChatServiceFacade(models,factory,info,retrieval,null,messages,null,null,null,null,null,null);
        var agent=new AgentVo();agent.setAgentName("合同审查官");agent.setSystemPrompt("输出issues");agent.setKnowledgeIds(List.of(12L));
        var model=new ChatModelVo();model.setProviderCode("vllm");var request=new ChatRequest();request.setUserId(1L);request.setSessionId(2L);request.setModel("model");request.setContent("审查违约责任");request.setChatModelVo(model);request.setEmitter(new SseEmitter());request.setContextMessages(List.of(SystemMessage.from("输出issues"),UserMessage.from(request.getContent())));
        when(factory.getOriginalService("vllm")).thenReturn(provider);when(provider.buildStreamingChatModel(model,request)).thenReturn(stream);
        ReflectionTestUtils.invokeMethod(facade,"handleAgentChat",request,agent,null);
        var sent=ArgumentCaptor.forClass(List.class);var callback=ArgumentCaptor.forClass(StreamingChatResponseHandler.class);verify(stream).chat(sent.capture(),callback.capture());
        assertTrue(((UserMessage)sent.getValue().get(1)).singleText().contains("real-selected-fid"));
        String raw="{\"summary\":\"摘要\",\"issues\":[{\"clauseTitle\":\"风险\",\"lawRefs\":[{\"lawName\":\"民法典\",\"articleNo\":\"585\",\"fragmentId\":null,\"sourceUrl\":null}]}]}";
        try(var sse=mockStatic(org.ruoyi.common.sse.utils.SseMessageUtils.class)) {
            callback.getValue().onPartialResponse(raw);verifyNoInteractions(messages);
            callback.getValue().onCompleteResponse(null);
            var saved=ArgumentCaptor.forClass(String.class);verify(messages).saveChatMessage(eq(1L),eq(2L),saved.capture(),eq("assistant"),eq("model"));
            var vo=new com.fasterxml.jackson.databind.ObjectMapper().readTree(saved.getValue());
            assertEquals("real-selected-fid",vo.path("issues").get(0).path("lawRefs").get(0).path("fragmentId").asText());
            sse.verify(()->org.ruoyi.common.sse.utils.SseMessageUtils.sendContent("2",saved.getValue()));
            sse.verify(()->org.ruoyi.common.sse.utils.SseMessageUtils.sendDone("2"));
        }
        verify(retrieval,times(1)).retrieve(any());
    }
    @org.junit.jupiter.api.Test
    void emptyKnowledgeIsExplicitAndNeverSuppliesCaseReferences() {
        var info = mock(org.ruoyi.service.knowledge.IKnowledgeInfoService.class);
        var models = mock(org.ruoyi.common.chat.service.chat.IChatModelService.class);
        var retrieval = mock(org.ruoyi.service.retrieval.KnowledgeRetrievalService.class);
        var kb = new org.ruoyi.domain.vo.knowledge.KnowledgeInfoVo(); kb.setId(12L); kb.setEmbeddingModel("embedding");
        when(info.queryById(12L)).thenReturn(kb); when(models.selectModelByName("embedding")).thenReturn(new ChatModelVo());
        when(retrieval.retrieve(any())).thenReturn(List.of());
        var facade = new ChatServiceFacade(models,null,info,retrieval,null,null,null,null,null,null,null,null);
        var agent = new AgentVo(); agent.setKnowledgeIds(List.of(12L));
        var request = new ChatRequest(); request.setContent("查找类案");
        String result = ReflectionTestUtils.invokeMethod(facade, "augmentAgentInput", request, agent);
        assertTrue(result.contains("知识库暂无匹配")); assertTrue(result.contains("禁止编造案号"));
        assertFalse(result.contains("caseNo")); verify(retrieval,times(1)).retrieve(any());
    }
    @org.junit.jupiter.api.Test
    @SuppressWarnings("unchecked")
    void knowledgeOnlyAgentKeepsSystemRoleAndRetrievesOnceWithoutSupervisor() {
        var factory = mock(ChatServiceFactory.class);
        var provider = mock(AbstractChatService.class);
        var stream = mock(StreamingChatModel.class);
        var info = mock(org.ruoyi.service.knowledge.IKnowledgeInfoService.class);
        var models = mock(org.ruoyi.common.chat.service.chat.IChatModelService.class);
        var retrieval = mock(org.ruoyi.service.retrieval.KnowledgeRetrievalService.class);
        var kb = new org.ruoyi.domain.vo.knowledge.KnowledgeInfoVo();
        kb.setId(12L); kb.setEmbeddingModel("embedding-3"); kb.setRetrieveLimit(3); kb.setEnableHybrid(1);
        when(info.queryById(12L)).thenReturn(kb);
        when(models.selectModelByName("embedding-3")).thenReturn(new ChatModelVo());
        when(retrieval.retrieve(any())).thenReturn(List.of(org.ruoyi.domain.vo.knowledge.KnowledgeRetrievalVo.builder()
            .id("abc").content("Verified statutory article: 577").docId("doc").build()));
        var facade = new ChatServiceFacade(models,factory,info,retrieval,null,null,null,null,null,null,null,null);
        var agent = new AgentVo();agent.setSystemPrompt("Return only JSON");agent.setKnowledgeIds(List.of(12L));
        var model = new ChatModelVo();model.setProviderCode("vllm");
        var request = new ChatRequest();request.setContent("Review contract");request.setChatModelVo(model);request.setEmitter(new SseEmitter());
        request.setContextMessages(List.of(SystemMessage.from(agent.getSystemPrompt()),UserMessage.from(request.getContent())));
        when(factory.getOriginalService("vllm")).thenReturn(provider);
        when(provider.buildStreamingChatModel(model,request)).thenReturn(stream);
        ReflectionTestUtils.invokeMethod(facade,"handleAgentChat",request,agent,null);
        var sent=ArgumentCaptor.forClass(List.class);verify(stream).chat(sent.capture(),any(StreamingChatResponseHandler.class));
        assertInstanceOf(SystemMessage.class,sent.getValue().get(0));
        assertTrue(((UserMessage)sent.getValue().get(1)).singleText().contains("Verified statutory article: 577"));
        verify(retrieval,times(1)).retrieve(any());verify(provider,never()).buildChatModel(any());
        assertNull(request.getGenerationTemperature(), "Other agents retain provider generation defaults");
    }
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SuppressWarnings("unchecked")
    void promptOnlyAgentSendsConfiguredPromptAsSystemMessage(boolean emptyLists) {
        var factory = mock(ChatServiceFactory.class);
        var provider = mock(AbstractChatService.class);
        var streamingModel = mock(StreamingChatModel.class);
        var model = new ChatModelVo();
        model.setProviderCode("vllm");
        var agent = new AgentVo();
        agent.setAgentName("合同审查官");
        agent.setSystemPrompt("Return only the configured contract-review JSON structure.");
        if (emptyLists) {
            agent.setMcpToolIds(List.of());
            agent.setSkillNames(List.of());
            agent.setKnowledgeIds(List.of());
        }
        var request = new ChatRequest();
        request.setUserId(1L);
        request.setContent("Review this contract fragment.");
        request.setChatModelVo(model);
        request.setEmitter(new SseEmitter());
        var facade = new ChatServiceFacade(null, factory, null, null, null, null,
            null, null, null, null, null, null);
        List<ChatMessage> context = ReflectionTestUtils.invokeMethod(facade,
            "buildContextMessages", request, agent);
        request.setContextMessages(context);
        when(factory.getOriginalService("vllm")).thenReturn(provider);
        when(provider.buildStreamingChatModel(model, request)).thenReturn(streamingModel);

        SseEmitter result = ReflectionTestUtils.invokeMethod(facade,
            "handleAgentChat", request, agent, null);

        var captured = ArgumentCaptor.forClass(List.class);
        verify(streamingModel).chat(captured.capture(), any(StreamingChatResponseHandler.class));
        List<ChatMessage> sent = captured.getValue();
        assertEquals(2, sent.size());
        assertEquals(agent.getSystemPrompt() + "\n" + org.ruoyi.domain.vo.knowledge.LegalReference.PROMPT + org.ruoyi.service.chat.ContractReviewControls.prompt(null,null), assertInstanceOf(SystemMessage.class, sent.get(0)).text());
        assertEquals(request.getContent(), assertInstanceOf(UserMessage.class, sent.get(1)).singleText());
        assertSame(request.getEmitter(), result);
        assertEquals(0.1, request.getGenerationTemperature());
        verify(provider, never()).buildChatModel(any(ChatModelVo.class));
    }
}
