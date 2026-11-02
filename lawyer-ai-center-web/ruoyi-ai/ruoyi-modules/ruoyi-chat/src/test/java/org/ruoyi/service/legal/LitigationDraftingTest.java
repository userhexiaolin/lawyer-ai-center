package org.ruoyi.service.legal;

import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class LitigationDraftingTest {
    static String request() { return "起草任务\n---要素JSON开始---\n{\"type\":\"民事起诉状\",\"plaintiff\":\"李四\",\"defendant\":\"张三\",\"claims\":\"返还借款10000元\",\"facts\":\"被告于2025年1月3日向原告借款10000元，约定2025年6月3日返还，现尚未返还。\",\"evidence\":\"借条、转账凭证\",\"court\":\"测试区人民法院\"}\n---要素JSON结束---"; }
    static String emptyDraft() { return "# 民事起诉状\n\n**原告：** {{原告}}\\\n**被告：** {{被告}}\n\n**诉讼请求：**\\\n{{诉讼请求}}\n\n**事实与理由：**\\\n{{事实与理由}}\n\n**证据与来源：**\\\n{{证据与来源}}\n\n**此致**\n{{受理法院待确认}}\n\n**具状人：** {{具状人}}\\\n**日期：** {{待填写}}"; }
    @Test void reproducerFillsAllProvidedFieldsAndRemovesCitationJson() {
        String raw = emptyDraft()+"\n\n---\n```json\n[{\"type\":\"law\",\"lawName\":\"测试法规\",\"articleNo\":\"第1条\",\"fragmentId\":\"internal-id\",\"sourceUrl\":null}]\n```";
        String result = LitigationDrafting.finish(raw, request());
        for(String required:List.of("李四","张三","返还借款10000元","被告于2025年1月3日","借条、转账凭证","测试区人民法院","【具状日期待填写】")) assertTrue(result.contains(required),required);
        assertFalse(result.contains("{{"));assertFalse(result.contains("fragmentId"));assertFalse(result.contains("```"));assertFalse(result.endsWith("---"));
    }
    @Test void emptyCoreSectionsAreRejectedRatherThanSavedAsADocument() {
        for(String text:List.of("# 民事起诉状\n原告：李四\n被告：张三","# 民事起诉状\n## 诉讼请求\n【待补充】\n## 事实与理由\n【待补充】")) assertThrows(DraftingException.class,()->LitigationDrafting.finish(text,request()));
    }
    @Test void stockProseCannotAddCollectionEventsOrUnrequestedFees() {
        String filled=LitigationDrafting.fill(emptyDraft(),LitigationDrafting.input(request()));
        String facts=LitigationDrafting.input(request()).facts();
        String raw=filled.replace(facts,"被告因资金周转借款10000元，经原告多次催讨未还。")
            .replace("返还借款10000元","1. 判令返还借款10000元；\n2. 判令被告承担全部诉讼费用。");
        String result=LitigationDrafting.finish(raw,request());
        assertTrue(result.contains(facts));assertFalse(result.contains("多次"));assertFalse(result.contains("资金周转"));assertFalse(result.contains("诉讼费"));assertTrue(result.contains("10000元"));
    }
    @Test void explicitlyRequestedFeesRemainInTheDraft() {
        String request=request().replace("返还借款10000元","返还借款10000元，并承担诉讼费");
        assertTrue(LitigationDrafting.finish(emptyDraft(),request).contains("诉讼费"));
    }
    @Test void wrongKindOfUploadedTemplateCannotGuideAComplaint() {
        assertThrows(DraftingException.class,()->LitigationDrafting.requireCompatible("民事起诉状","律 师 函（模板）\n致：某公司"));
        assertDoesNotThrow(()->LitigationDrafting.requireCompatible("律师函","律 师 函（模板）\n致：某公司"));
        assertDoesNotThrow(()->LitigationDrafting.requireCompatible("民事起诉状","# 民事起诉状\n事实中曾发送律师函"));
    }
    @Test void missingOptionalFieldsAreExplicitWithoutLeavingRawTemplateSlots() {
        String source=request().replace("借条、转账凭证","").replace("测试区人民法院","");
        String result=LitigationDrafting.finish(emptyDraft(),source);
        assertTrue(result.contains("【证据及来源待补充】"));assertTrue(result.contains("【受理法院待确认】"));assertFalse(result.contains("{{"));
    }
    @Test void unknownTemplateFieldsAreNamedForReviewWithoutInventedData() {
        String filled=LitigationDrafting.fill("{{原告}} {{身份证号}}",LitigationDrafting.input(request()));
        assertEquals("李四 【身份证号待补充】",filled);
    }
    @Test void citationCleanupPreservesUnrelatedJsonExamples() {
        String raw="# 正文\n```json\n{\"合同编号\":\"A-1\"}\n```";
        assertEquals(raw,LitigationDrafting.cleanDocument(raw));
    }
    @Test void generatedDraftIsBufferedAndOnlyCheckedTextIsEmitted() {
        var delegate=mock(StreamingChatResponseHandler.class);var handler=LitigationDrafting.wrap(delegate,request());
        handler.onPartialResponse(emptyDraft());verifyNoInteractions(delegate);
        handler.onCompleteResponse(null);
        verify(delegate).onPartialResponse(LitigationDrafting.finish(emptyDraft(),request()));verify(delegate).onCompleteResponse(null);
        handler.onCompleteResponse(null);verifyNoMoreInteractions(delegate);
    }
    @Test void failedQualityCheckNeverEmitsTheBrokenPaper() {
        var delegate=mock(StreamingChatResponseHandler.class);var handler=LitigationDrafting.wrap(delegate,request());
        handler.onPartialResponse("# 民事起诉状\n原告：李四");handler.onCompleteResponse(null);
        verify(delegate,never()).onPartialResponse(anyString());verify(delegate).onError(any(DraftingException.class));verify(delegate,never()).onCompleteResponse(any());
    }
    @Test void userFieldsCannotConsistOfUnfilledTemplateSlots() {
        assertThrows(DraftingException.class,()->LitigationDrafting.input(request().replace("返还借款10000元","{{诉讼请求}}")));
    }
    @Test void factsAcceptTwentyThousandAndRejectTheNextCharacterBeforeModelUse() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        for (String type : List.of("民事起诉状", "民事答辩状")) {
            for (int length : List.of(20_000, 20_001)) {
                String json = mapper.writeValueAsString(java.util.Map.of("type", type, "plaintiff", "合成甲",
                    "defendant", "合成乙", "claims", "返还合成款项", "facts", "字".repeat(length)));
                String source = "---要素JSON开始---\n" + json + "\n---要素JSON结束---";
                if (length == 20_000) assertEquals(length, LitigationDrafting.input(source).facts().length());
                else assertTrue(assertThrows(DraftingException.class, () -> LitigationDrafting.input(source)).getMessage().contains("20,000"));
            }
        }
    }
    @Test void downloadAndSavedDraftCannotContainUnfilledTemplateFields() {
        assertThrows(org.ruoyi.common.core.exception.ServiceException.class,()->DocumentWord.normalize(emptyDraft()));
        assertFalse(DocumentWord.normalize(LitigationDrafting.finish(emptyDraft(),request())).contains("{{"));
    }
}
