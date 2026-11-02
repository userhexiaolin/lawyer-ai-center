package org.ruoyi.service.knowledge;
import org.junit.jupiter.api.*;
import org.ruoyi.domain.entity.knowledge.KnowledgeAttach;
import org.ruoyi.domain.vo.knowledge.KnowledgeRetrievalVo;
import org.ruoyi.mapper.knowledge.KnowledgeAttachMapper;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@Tag("dev")
class LegalReferenceTest {
    @Test void metadataRetainsStatutoryAndCaseIdentifiersAndSource() {
        var mapper = mock(KnowledgeAttachMapper.class);
        var attach = new KnowledgeAttach(); attach.setKnowledgeId(1L); attach.setDocId("doc");
        attach.setLawName("测试法规"); attach.setArticleNo("第12条"); attach.setValidityStatus("已失效"); attach.setSourceUrl("https://example.org/law");
        when(mapper.selectList(any())).thenReturn(List.of(attach));
        var result = KnowledgeRetrievalVo.builder().id("fid").docId("doc").knowledgeId(1L).content("原文").build();
        var service = new LegalMetadataService(mapper); service.enrich(List.of(result));
        assertEquals("测试法规", result.getReference().lawName()); assertEquals("第12条", result.getReference().articleNo());
        assertEquals("repealed", result.getReference().status()); assertEquals("fid", result.getReference().fragmentId());
        assertEquals("https://example.org/law", result.getReference().sourceUrl());
        attach.setCaseNo("（测试）1号"); attach.setCourt("测试法院"); attach.setName("测试类案"); service.enrich(List.of(result));
        assertEquals("case", result.getReference().type()); assertEquals("（测试）1号", result.getReference().caseNo()); assertEquals("测试法院", result.getReference().court());
    }
    @Test void missingMetadataNeverCreatesCaseNumberAndExpiryOverridesEffective() {
        var mapper = mock(KnowledgeAttachMapper.class); when(mapper.selectList(any())).thenReturn(List.of());
        var row = KnowledgeRetrievalVo.builder().id("f").docId("doc").knowledgeId(1L).content("无案号的正文").build();
        new LegalMetadataService(mapper).enrich(List.of(row)); assertNull(row.getReference());
        assertEquals("repealed", LegalMetadataService.canonicalStatus(new LegalMetadataService.LegalMetadata("测试", "1", null, "effective", null, java.time.LocalDate.of(2000,1,1), null)));
    }
}
