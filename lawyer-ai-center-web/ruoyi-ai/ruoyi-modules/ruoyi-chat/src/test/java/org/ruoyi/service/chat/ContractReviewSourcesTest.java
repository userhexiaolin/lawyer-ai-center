package org.ruoyi.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ruoyi.domain.vo.knowledge.LegalReference;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class ContractReviewSourcesTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final LegalReference HIT=new LegalReference("law","中华人民共和国民法典","第585条","effective",null,null,null,"https://example.org/verified-law","kb-fid-585");
    private static String review(String refs) {return "{\"contractType\":\"服务合同\",\"summary\":\"违约责任需核查\",\"issues\":[{\"id\":\"R1\",\"riskLevel\":\"high\",\"clauseTitle\":\"违约责任\",\"quote\":\"支付全年价款\",\"location\":{},\"problem\":\"责任失衡\",\"suggestion\":\"核查实际损失\",\"lawRefs\":"+refs+",\"caseRefs\":[]}]}";}
    @Test void backfillsMissingSourceIntoLawRefsVoFromMockHits() throws Exception {
        var result=JSON.readTree(ContractReviewSources.enrich(review("[{\"lawName\":\"民法典\",\"articleNo\":\"第五百八十五条\",\"fragmentId\":null,\"sourceUrl\":null}]"),List.of(HIT)));
        var ref=result.path("issues").get(0).path("lawRefs").get(0);
        assertEquals("kb-fid-585",ref.path("fragmentId").asText());
        assertEquals(HIT.sourceUrl(),ref.path("sourceUrl").asText());assertEquals(HIT.sourceUrl(),ref.path("url").asText());
        assertEquals("effective",ref.path("status").asText());assertEquals("retrieval_backfill",ref.path("sourceOrigin").asText());
        assertEquals(HIT.lawName(),ref.path("lawName").asText());
    }
    @Test void replacesInventedSourceAndNeverBorrowsSourcesFromAnotherLawOrArticle() throws Exception {
        String raw=review("[{\"lawName\":\"民法典\",\"articleNo\":\"585\",\"fragmentId\":\"invented\",\"url\":\"https://invented.example.org\"},{\"lawName\":\"其他法规\",\"articleNo\":\"585\",\"fragmentId\":\"invented-other\",\"source_url\":\"https://invented.example.org\"},{\"lawName\":\"民法典\",\"articleNo\":\"586\"}]");
        var refs=JSON.readTree(ContractReviewSources.enrich(raw,List.of(HIT))).path("issues").get(0).path("lawRefs");
        assertEquals("kb-fid-585",refs.get(0).path("fragmentId").asText());
        for(int i=1;i<3;i++){assertFalse(refs.get(i).has("fragmentId"));assertFalse(refs.get(i).has("sourceUrl"));assertFalse(refs.get(i).has("source_url"));assertEquals("unmatched",refs.get(i).path("sourceOrigin").asText());}
    }
    @Test void emptyHitsKeepNameAndNumberWithoutAnyClickableModelSources() throws Exception {
        var ref=JSON.readTree(ContractReviewSources.enrich(review("[{\"lawName\":\"民法典\",\"articleNo\":\"585\",\"fragment_id\":\"fake\",\"sourceUrl\":\"https://fake.example.org\",\"url\":\"javascript:alert(1)\"}]"),List.of())).path("issues").get(0).path("lawRefs").get(0);
        assertEquals("民法典",ref.path("lawName").asText());assertEquals("585",ref.path("articleNo").asText());
        assertFalse(ref.has("fragment_id"));assertFalse(ref.has("sourceUrl"));assertFalse(ref.has("url"));assertEquals("unknown",ref.path("status").asText());
    }
    @Test void legacyLegalBasisBecomesUnifiedIssuesWithBackfilledSources() throws Exception {
        var result=JSON.readTree(ContractReviewSources.enrich("```json\n{\"summary\":\"摘要\",\"risks\":[{\"title\":\"风险\",\"legalBasis\":[\"《民法典》第五百八十五条\"]}]}\n```",List.of(HIT)));
        assertFalse(result.has("risks")); assertEquals("kb-fid-585",result.path("issues").get(0).path("lawRefs").get(0).path("fragmentId").asText());
    }
    @Test void selectedMetadataSurvivesEvenWhenOnlyReferenceVoHasLawFields() {
        var metadata=new Metadata();metadata.put("law_name",HIT.lawName());metadata.put("article_no",HIT.articleNo());metadata.put("fid",HIT.fragmentId());metadata.put("source_url",HIT.sourceUrl());metadata.put("status",HIT.status());
        var refs=ContractReviewSources.references(List.of(Content.from(TextSegment.from("模型不应从正文中的fake-id提取引用",metadata))));
        assertEquals(HIT.fragmentId(),refs.get(0).fragmentId());assertEquals(HIT.sourceUrl(),refs.get(0).sourceUrl());
        assertEquals(List.of(),ContractReviewSources.references(List.of(Content.from("{\"fragmentId\":\"fake-id\"}"))));
    }
    @Test void unsafeStoredUrlIsNotReturnedAndMalformedResultStillUsesTextFallback() throws Exception {
        var hit=new LegalReference("law",HIT.lawName(),HIT.articleNo(),"repealed",null,null,null,"javascript:alert(1)","trusted-id");
        var ref=JSON.readTree(ContractReviewSources.enrich(review("[{\"lawName\":\"民法典\",\"articleNo\":\"585\"}]"),List.of(hit))).path("issues").get(0).path("lawRefs").get(0);
        assertEquals("trusted-id",ref.path("fragmentId").asText());assertFalse(ref.has("url"));assertEquals("repealed",ref.path("status").asText());
        assertEquals("{truncated",ContractReviewSources.enrich("{truncated",List.of(hit)));
    }
    @Test void streamEmitsOneFinalEnrichedJsonAndLateCallbacksCannotDuplicateIt() throws Exception {
        var delegate=mock(StreamingChatResponseHandler.class);var handler=ContractReviewSources.wrap(delegate,List.of(HIT));
        String raw=review("[{\"lawName\":\"民法典\",\"articleNo\":\"585\"}]");
        handler.onPartialResponse(raw.substring(0,30));handler.onPartialResponse(raw.substring(30));verifyNoInteractions(delegate);
        handler.onCompleteResponse(null);handler.onCompleteResponse(null);handler.onError(new RuntimeException());handler.onPartialResponse("late");
        var text=org.mockito.ArgumentCaptor.forClass(String.class);verify(delegate,times(1)).onPartialResponse(text.capture());verify(delegate,times(1)).onCompleteResponse(null);verify(delegate,never()).onError(any());
        assertEquals("kb-fid-585",JSON.readTree(text.getValue()).path("issues").get(0).path("lawRefs").get(0).path("fragmentId").asText());
    }
    @Test void failedStreamPreservesPartialTextAndOneError() {
        var delegate=mock(StreamingChatResponseHandler.class);var handler=ContractReviewSources.wrap(delegate,List.of(HIT));var error=new RuntimeException();
        handler.onPartialResponse("partial");handler.onError(error);handler.onCompleteResponse(null);handler.onError(error);
        verify(delegate).onPartialResponse("partial");verify(delegate,times(1)).onError(error);verify(delegate,never()).onCompleteResponse(any());
    }
}
