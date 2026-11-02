package org.ruoyi.service.knowledge;
import org.junit.jupiter.api.*;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.domain.vo.knowledge.*;
import org.ruoyi.service.knowledge.retriever.CustomVectorRetriever;
import org.ruoyi.service.retrieval.KnowledgeRetrievalService;
import dev.langchain4j.rag.query.Query;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@Tag("dev")
class LegalCitationRetrieverTest {
    @Test void structuredCaseMetadataSurvivesRagAssembly() {
        var retrieval = mock(KnowledgeRetrievalService.class);
        var result = new KnowledgeRetrievalVo(); result.setId("fid"); result.setContent("真实类案内容");
        result.setReference(new LegalReference("case", null, null, null, "（测试）1号", "测试类案", "测试法院", "https://example.org/case", "fid"));
        when(retrieval.retrieve(any())).thenReturn(List.of(result));
        var info = new KnowledgeInfoVo(); info.setId(1L);
        var segment = new CustomVectorRetriever(retrieval, info, new ChatModelVo()).retrieve(Query.from("问题")).get(0).textSegment();
        assertTrue(segment.text().contains("（测试）1号")); assertTrue(segment.text().contains("fragmentId"));
        assertEquals("（测试）1号", segment.metadata().getString("case_no")); assertEquals("fid", segment.metadata().getString("fragmentId"));
    }
    @Test void onlySuccessfulRerankingCarriesComparableModelAndScoreMetadata() {
        var retrieval = mock(KnowledgeRetrievalService.class);
        var ranked = new KnowledgeRetrievalVo(); ranked.setId("ranked"); ranked.setContent("已重排"); ranked.setScore(.91); ranked.setRawScore(.02);
        var fallback = new KnowledgeRetrievalVo(); fallback.setId("fallback"); fallback.setContent("粗召回"); fallback.setScore(.03);
        when(retrieval.retrieve(any())).thenReturn(List.of(ranked, fallback));
        var info = new KnowledgeInfoVo(); info.setId(1L); info.setRerankModel("tei-rerank");
        var contents = new CustomVectorRetriever(retrieval, info, new ChatModelVo()).retrieve(Query.from("问题"));
        assertEquals("tei-rerank", contents.get(0).textSegment().metadata().getString("rerank_model"));
        assertEquals("0.91", contents.get(0).textSegment().metadata().getString("rerank_score"));
        assertNull(contents.get(1).textSegment().metadata().getString("rerank_model"));
    }
    @Test void augmentedTextAndSegmentMetadataCarryExactLegalCitation() {
        var retrieval = mock(KnowledgeRetrievalService.class);
        var result = new KnowledgeRetrievalVo(); result.setDocId("doc"); result.setId("fid"); result.setContent("原始条款内容");
        result.setLegalMetadata(new LegalMetadataService.LegalMetadata("测试法规","第1条","全国","有效",null,null,"版本1"));
        when(retrieval.retrieve(any())).thenReturn(List.of(result));
        var info = new KnowledgeInfoVo(); info.setId(1L); var model = new ChatModelVo();
        var contents = new CustomVectorRetriever(retrieval, info, model).retrieve(Query.from("关键词"));
        var segment = contents.get(0).textSegment();
        assertTrue(segment.text().contains("《测试法规》第1条（有效）")); assertTrue(segment.text().contains("原始条款内容"));
        assertEquals("测试法规",segment.metadata().getString("law_name")); assertEquals("版本1",segment.metadata().getString("source_version"));
    }
}
