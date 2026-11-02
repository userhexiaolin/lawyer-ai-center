package org.ruoyi.service.retrieval.impl;
import org.junit.jupiter.api.*;
import org.ruoyi.common.trace.config.TraceProperties;
import org.ruoyi.common.trace.service.TraceRecordService;
import org.ruoyi.domain.bo.vector.QueryVectorBo;
import org.ruoyi.domain.vo.knowledge.KnowledgeFragmentVo;
import org.ruoyi.factory.RerankModelFactory;
import org.ruoyi.mapper.knowledge.KnowledgeFragmentMapper;
import org.ruoyi.service.vector.VectorStoreService;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("dev")
class HybridKeywordFallbackTest {
    @Test void unavailableVectorAndMissingFulltextStillReturnRealSqlFragments() {
        var vector = mock(VectorStoreService.class); var mapper = mock(KnowledgeFragmentMapper.class);
        when(vector.search(any())).thenThrow(new IllegalStateException("Vector offline"));
        when(mapper.searchByKeyword(anyLong(), anyString(), anyInt())).thenThrow(new IllegalStateException("Fulltext index missing"));
        var fragment = new KnowledgeFragmentVo(); fragment.setId(9L); fragment.setFid("real-fid"); fragment.setKnowledgeId(1L); fragment.setDocId("real-doc"); fragment.setContent("真实已存储的关键词资料");
        when(mapper.searchByLiteral(eq(1L), eq("关键词"), anyInt())).thenReturn(List.of(fragment));
        var trace = new TraceProperties(); trace.setEnabled(false);
        var service = new KnowledgeRetrievalServiceImpl(vector, mock(RerankModelFactory.class), mapper, mock(TraceRecordService.class), trace);
        var query = new QueryVectorBo(); query.setKid("1"); query.setQuery("关键词"); query.setEnableHybrid(true); query.setMaxResults(5);
        var result = service.retrieve(query);
        assertEquals(1, result.size()); assertEquals("real-fid", result.get(0).getId()); assertEquals("真实已存储的关键词资料", result.get(0).getContent());
        verify(mapper).searchByLiteral(eq(1L),eq("关键词"),anyInt());
    }
}
