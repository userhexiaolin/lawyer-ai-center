package org.ruoyi.service.knowledge;
import org.junit.jupiter.api.*;
import org.ruoyi.domain.entity.knowledge.KnowledgeAttach;
import org.ruoyi.domain.vo.knowledge.KnowledgeRetrievalVo;
import org.ruoyi.mapper.knowledge.KnowledgeAttachMapper;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@Tag("dev")
class LegalMetadataServiceTest {
    @Test void oldRecordsRemainCompatibleAndUnknownValidityIsNotAssumedValid() {
        var mapper = mock(KnowledgeAttachMapper.class); when(mapper.selectList(any())).thenReturn(List.of());
        var service = new LegalMetadataService(mapper);
        var result = new KnowledgeRetrievalVo(); result.setDocId("old"); result.setContent("原文");
        service.enrich(List.of(result)); assertEquals("", result.getCitation()); assertEquals("原文", result.getContent());
        assertEquals("《测试法规》第1条（时效待核实）", new LegalMetadataService.LegalMetadata("测试法规", "1", null, null, null, null, null).citation());
    }
    @Test void metadataIsInheritedByExactKnowledgeAndDocumentAndUpdatesStayFresh() {
        var mapper = mock(KnowledgeAttachMapper.class); var attach = new KnowledgeAttach();
        attach.setDocId("doc"); attach.setKnowledgeId(10L); attach.setLawName("测试法规"); attach.setArticleNo("第2条"); attach.setValidityStatus("有效"); attach.setRegion("全国");
        when(mapper.selectList(any())).thenReturn(List.of(attach)); var service = new LegalMetadataService(mapper);
        var own = new KnowledgeRetrievalVo(); own.setDocId("doc"); own.setKnowledgeId(10L);
        var other = new KnowledgeRetrievalVo(); other.setDocId("doc"); other.setKnowledgeId(11L);
        service.enrich(List.of(own, other)); assertEquals("《测试法规》第2条（有效）", own.getCitation()); assertNull(other.getLegalMetadata());
        attach.setExpireDate(LocalDate.of(2000,1,1)); service.enrich(List.of(own)); assertTrue(own.getCitation().contains("已失效"));
    }
}
