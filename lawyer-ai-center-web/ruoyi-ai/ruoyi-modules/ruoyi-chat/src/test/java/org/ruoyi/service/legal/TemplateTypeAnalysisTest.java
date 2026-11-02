package org.ruoyi.service.legal;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.vo.agent.AgentVo;
import org.ruoyi.factory.ChatServiceFactory;
import org.ruoyi.service.agent.IAgentService;
import org.ruoyi.service.chat.AbstractChatService;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("dev")
class TemplateTypeAnalysisTest {
    private DocTemplateTypeService types;
    private IAgentService agents;
    private IChatModelService models;
    private ChatServiceFactory factory;
    private AbstractChatService provider;
    private ChatModel chat;
    private DocumentConversion conversion;
    private ChatModelVo config;
    private TemplateTypeAnalysis service;
    private MockedStatic<LoginHelper> login;
    private DocTemplateService templates;
    private TemplateFiles files;
    private TemplateTypeDecisionStore decisions;
    private final Map<String,TemplateTypeDecisionStore.Decision> memory=new HashMap<>();

    @BeforeEach void setup() {
        types = mock(DocTemplateTypeService.class); agents = mock(IAgentService.class); models = mock(IChatModelService.class);
        factory = mock(ChatServiceFactory.class); provider = mock(AbstractChatService.class); chat = mock(ChatModel.class); conversion = mock(DocumentConversion.class);
        var agent = new AgentVo(); agent.setId(4L); agent.setAgentName("文书起草助手"); agent.setModelId(7L); agent.setStatus("0");
        when(agents.queryEnabledOptions()).thenReturn(List.of(agent));
        config = new ChatModelVo(); config.setId(7L); config.setModelName("test-qwen"); config.setProviderCode("vllm");
        config.setApiHost("http://127.0.0.1:9000/v1"); config.setCategory("chat");
        when(models.queryById(7L)).thenReturn(config); when(models.selectModelByName("test-qwen")).thenReturn(config);
        when(factory.getOriginalService("vllm")).thenReturn(provider); when(provider.buildChatModel(config)).thenReturn(chat);
        when(types.options(false)).thenReturn(List.of(new DocTemplateTypeService.Option("律师函", "律师函", false)));
        templates=mock(DocTemplateService.class);files=mock(TemplateFiles.class);decisions=mock(TemplateTypeDecisionStore.class);memory.clear();
        service = new TemplateTypeAnalysis(types, agents, models, factory, conversion,
            new TemplateTypeOutboundGate(7L, "test-qwen", "vllm", "http://127.0.0.1:9000/v1"),templates,files,decisions);
        login = mockStatic(LoginHelper.class); login.when(LoginHelper::getUserId).thenReturn(3L); login.when(LoginHelper::getTenantId).thenReturn("tenant-test");
        when(decisions.find(anyString())).thenAnswer(call->Optional.ofNullable(memory.get(LoginHelper.getTenantId()+call.getArgument(0))));
        when(decisions.retain(anyString(),anyString(),anyLong(),anyString(),anyString(),any())).thenAnswer(call->memory.computeIfAbsent(LoginHelper.getTenantId()+call.getArgument(0),key->call.getArgument(5)));
    }
    @AfterEach void close() { service.close(); login.close(); }
    private MockMultipartFile file() { return new MockMultipartFile("file", "模板01.txt", "text/plain", "合成模板\n致合成测试公司：请履行义务。".getBytes(StandardCharsets.UTF_8)); }
    private void reply(String json) {
        String value=json.startsWith("{")&&json.endsWith("}")&&!json.contains("\"evidence\"")?json.substring(0,json.length()-1)+",\"evidence\":\"请履行义务\"}":json;
        doReturn(ChatResponse.builder().aiMessage(AiMessage.from(value)).build()).when(chat).chat(any(ChatRequest.class));
    }

    @Test void existingTemplateReturnsVersionBoundLocalSuggestionWithoutWritingOrReadingBinaryWhenTextExists(){
        when(templates.typeSnapshot(9L)).thenReturn(new DocTemplateService.TypeSnapshot(9L,"原类型","律师函正文","source1","revision1"));
        reply("{\"documentType\":\"律师函\",\"evidence\":\"律师函正文\"}");var result=service.analyzeExisting(9L);
        assertEquals("律师函",result.documentType());assertEquals("revision1",result.revision());assertEquals("原类型",result.previousType());
        verify(templates).typeSnapshot(9L);verifyNoMoreInteractions(templates);verifyNoInteractions(files);
    }

    @Test void classifiesOnlyIntoEnabledTypesThroughTheApprovedGateWithoutCreatingTemplates() {
        reply("{\"documentType\":\"律师函\"}");
        var result = service.analyze(file());
        assertEquals("律师函", result.documentType()); assertTrue(result.warning().contains("待人工核对"));
        verify(chat).chat(any(ChatRequest.class)); verifyNoInteractions(conversion);
    }
    @Test void unknownMalformedAndNullModelAnswersRemainOptionalFallbacks() {
        for (String answer : List.of("{\"documentType\":null}", "{\"documentType\":\"不存在\"}", "{\"documentType\":[]}", "not-json")) {
            memory.clear();
            reply(answer); assertNull(service.analyze(file()).documentType());
        }
    }
    @Test void unavailableModelAndUnreadableFileDoNotBecomeUploadFailures() {
        when(chat.chat(any(ChatRequest.class))).thenThrow(new IllegalStateException("synthetic outage"));
        assertNull(service.analyze(file()).documentType());
        reset(factory); assertNull(service.analyze(new MockMultipartFile("file", "空白.txt", "text/plain", new byte[0])).documentType());
        verifyNoInteractions(factory);
    }
    @Test void unauthorizedTargetIsRejectedBeforeFileBytesAndProviderConstruction() {
        var file = mock(org.springframework.web.multipart.MultipartFile.class);
        config.setApiHost("https://unapproved.invalid/v1");
        assertNull(service.analyze(file).documentType()); verifyNoInteractions(file, factory, conversion);
    }
    @Test void missingLoginFailsBeforeAllDataOrProviderAccess() {
        login.when(LoginHelper::getUserId).thenReturn(null);
        assertEquals(401, assertThrows(DraftingException.class, () -> service.analyze(file())).getCode());
        verifyNoInteractions(types, agents, models, factory, conversion);
    }
    @Test void revalidatesEnabledTypesAfterModelResponseAndLimitsTheExcerpt() {
        reply("{\"documentType\":\"律师函\"}");
        when(types.options(false)).thenReturn(List.of(new DocTemplateTypeService.Option("律师函", "律师函", false)), List.of());
        assertNull(service.analyze(file()).documentType());
        assertEquals(6000, TemplateTypeAnalysis.excerpt("头".repeat(7000) + "尾".repeat(1000)).length());
        assertTrue(TemplateTypeAnalysis.excerpt("头".repeat(7000) + "尾".repeat(1000)).endsWith("尾".repeat(999)));
        assertEquals("短模板", TemplateTypeAnalysis.excerpt("短模板"));
    }
    @Test void exactTitleWinsOverReferencesToOtherDocumentFormsWithoutCallingTheModel(){
        when(types.options(false)).thenReturn(List.of(new DocTemplateTypeService.Option("合同文书","合同文书",false),new DocTemplateTypeService.Option("律师函","律师函",false)));
        var file=new MockMultipartFile("file","无类型名称.txt","text/plain","律师函\n本函依据借款合同催告履行义务。".getBytes(StandardCharsets.UTF_8));
        var result=service.analyze(file);
        assertEquals("律师函",result.documentType());assertTrue(result.warning().contains("正文标题匹配"));
        verifyNoInteractions(factory,chat);
    }
    @Test void identicalInputReusesTheSuggestionAcrossServiceInstancesAndCandidateOrdering(){
        var a=new DocTemplateTypeService.Option("律师函","律师函",false);var b=new DocTemplateTypeService.Option("合同文书","合同文书",false);
        when(types.options(false)).thenReturn(List.of(b,a),List.of(a,b));
        reply("{\"documentType\":\"律师函\"}");
        assertEquals("律师函",service.analyze(file()).documentType());
        reply("{\"documentType\":\"合同文书\"}");
        for(int i=0;i<5;i++)assertEquals("律师函",service.analyze(file()).documentType());
        var other=new TemplateTypeAnalysis(types,agents,models,factory,conversion,new TemplateTypeOutboundGate(7L,"test-qwen","vllm","http://127.0.0.1:9000/v1"),templates,files,decisions);
        try{assertTrue(other.analyze(file()).warning().contains("复用相同内容"));}finally{other.close();}
        verify(chat,times(1)).chat(any(ChatRequest.class));
    }
    @Test void fixedRequestParametersAndVerbatimEvidenceAreRequired(){
        reply("{\"documentType\":\"律师函\",\"evidence\":\"正文中不存在的引文\"}");
        assertNull(service.analyze(file()).documentType());
        assertTrue(memory.isEmpty());
        var capture=org.mockito.ArgumentCaptor.forClass(ChatRequest.class);verify(chat).chat(capture.capture());
        var p=(OpenAiChatRequestParameters)capture.getValue().parameters();
        assertEquals(0.0,p.temperature());assertEquals(1.0,p.topP());assertEquals(0,p.seed());assertEquals(512,p.maxOutputTokens());
        assertEquals(Map.of("enable_thinking",false),p.customParameters().get("chat_template_kwargs"));
    }
    @Test void changedContentAndChangedCandidateSetInvalidateThePreviousSuggestion(){
        reply("{\"documentType\":\"律师函\"}");service.analyze(file());
        var changed=new MockMultipartFile("file","模板01.txt","text/plain","合成模板已修改\n请履行义务。".getBytes(StandardCharsets.UTF_8));
        service.analyze(changed);
        when(types.options(false)).thenReturn(List.of(new DocTemplateTypeService.Option("律师函","律师函",false),new DocTemplateTypeService.Option("合同文书","合同文书",false)));
        service.analyze(file());verify(chat,times(3)).chat(any(ChatRequest.class));
    }
    @Test void failuresAreRetryableRatherThanBeingFrozenAsSuccessfulSnapshots(){
        when(chat.chat(any(ChatRequest.class))).thenThrow(new IllegalStateException("synthetic outage"));
        assertNull(service.analyze(file()).documentType());assertTrue(memory.isEmpty());
        reply("{\"documentType\":\"律师函\"}");assertEquals("律师函",service.analyze(file()).documentType());
        verify(chat,times(2)).chat(any(ChatRequest.class));
    }

    @Test void missingSuggestionTableIsActionableAndMigrationRetryRecognizesOriginalPropertyDocx()throws Exception{
        var source=java.nio.file.Path.of(System.getProperty("template.fixtureRoot","."),"artifacts/20261022/物业服务合同20261022/03_物业费催缴律师函模板.docx");
        Assumptions.assumeTrue(java.nio.file.Files.exists(source),"Local original DOCX fixture required");
        byte[] before=java.nio.file.Files.readAllBytes(source);
        var original=new MockMultipartFile("file",source.getFileName().toString(),"application/vnd.openxmlformats-officedocument.wordprocessingml.document",before);
        var missing=new org.springframework.jdbc.BadSqlGrammarException("classification cache","SELECT private_synthetic_value",new java.sql.SQLException("synthetic private connection details","42S02",1146));
        when(decisions.find(anyString())).thenThrow(missing).thenReturn(Optional.empty());
        var failed=service.analyze(original);
        assertNull(failed.documentType());assertTrue(failed.warning().contains("尚未初始化"));assertTrue(failed.warning().contains("数据库升级"));
        assertFalse(failed.warning().contains("private"));assertTrue(memory.isEmpty());verifyNoInteractions(factory,chat);
        var retried=service.analyze(original);
        assertEquals("律师函",retried.documentType());assertTrue(retried.warning().contains("正文标题匹配"));
        verifyNoInteractions(factory,chat);assertArrayEquals(before,java.nio.file.Files.readAllBytes(source));
    }

    @Test void cacheReadAndWriteOutagesHaveSafeSpecificReasons(){
        when(decisions.find(anyString())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic secret connection"));
        var failed=service.analyze(file());assertNull(failed.documentType());assertTrue(failed.warning().contains("存储暂不可用"));assertFalse(failed.warning().contains("secret"));
        doReturn(Optional.empty()).when(decisions).find(anyString());
        when(decisions.retain(anyString(),anyString(),anyLong(),anyString(),anyString(),any()))
            .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic secret connection"));
        reply("{\"documentType\":\"律师函\"}");
        var write=service.analyze(file());assertNull(write.documentType());assertTrue(write.warning().contains("存储暂不可用"));assertFalse(write.warning().contains("secret"));
    }

    @Test void fileFailuresModelFailuresAndMissingAssistantHaveDifferentRecoveryMessages(){
        var unreadable=service.analyze(new MockMultipartFile("file","空白.txt","text/plain",new byte[0]));
        assertNull(unreadable.documentType());assertTrue(unreadable.warning().contains("模板文件无效"));
        when(chat.chat(any(ChatRequest.class))).thenThrow(new IllegalStateException("synthetic secret upstream"));
        var unavailable=service.analyze(file());assertTrue(unavailable.warning().contains("模型调用失败"));assertFalse(unavailable.warning().contains("secret"));
        when(agents.queryEnabledOptions()).thenReturn(List.of());
        var missing=service.analyze(file());assertTrue(missing.warning().contains("未配置可用的文书起草助手"));
    }

    @Test void timeoutAndBusyWorkersAreRetryableAndDoNotCacheSuggestions()throws Exception{
        var executor=mock(java.util.concurrent.ExecutorService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"workers",executor);
        var pending=mock(java.util.concurrent.Future.class);
        when(executor.submit(org.mockito.ArgumentMatchers.<java.util.concurrent.Callable<String>>any())).thenReturn(pending);
        when(pending.get(45,java.util.concurrent.TimeUnit.SECONDS)).thenThrow(new java.util.concurrent.TimeoutException());
        assertTrue(service.analyze(file()).warning().contains("响应超时"));verify(pending).cancel(true);
        when(executor.submit(org.mockito.ArgumentMatchers.<java.util.concurrent.Callable<String>>any())).thenThrow(new java.util.concurrent.RejectedExecutionException());
        assertTrue(service.analyze(file()).warning().contains("服务繁忙"));assertTrue(memory.isEmpty());verifyNoInteractions(factory,chat);
    }
}
