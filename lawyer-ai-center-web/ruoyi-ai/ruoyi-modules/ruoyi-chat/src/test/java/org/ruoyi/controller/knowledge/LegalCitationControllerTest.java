package org.ruoyi.controller.knowledge;
import org.junit.jupiter.api.*;
import org.ruoyi.service.knowledge.*;
import org.ruoyi.mapper.knowledge.KnowledgeFragmentMapper;
import org.ruoyi.domain.entity.knowledge.KnowledgeFragment;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class LegalCitationControllerTest {
    @Test void rejectsInaccessibleAndMissingFragmentsWithoutReturningContent() {
        var access = mock(KnowledgeAccess.class); var mapper = mock(KnowledgeFragmentMapper.class); var metadata = mock(LegalMetadataService.class);
        var controller = new LegalCitationController(new LegalCitationService(access, mapper, metadata));
        when(access.readableIds()).thenReturn(List.of()); assertThrows(RuntimeException.class, () -> controller.detail("f")); verifyNoInteractions(mapper, metadata);
        when(access.readableIds()).thenReturn(List.of(1L)); when(mapper.selectList(any())).thenReturn(List.of());
        assertThrows(RuntimeException.class, () -> controller.detail("f")); verifyNoInteractions(metadata);
    }
    @Test void authorizedFragmentKeepsOriginalContentAndIsEnriched() {
        var access = mock(KnowledgeAccess.class); var mapper = mock(KnowledgeFragmentMapper.class); var metadata = mock(LegalMetadataService.class);
        when(access.readableIds()).thenReturn(List.of(1L));
        var fragment = new KnowledgeFragment(); fragment.setKnowledgeId(1L); fragment.setDocId("doc"); fragment.setContent("原文"); fragment.setFid("f");
        when(mapper.selectList(any())).thenReturn(List.of(fragment)); when(metadata.enrich(any())).thenAnswer(i -> i.getArgument(0));
        var result = new LegalCitationController(new LegalCitationService(access, mapper, metadata)).detail("f").getData();
        verify(access).read(1L); assertEquals("原文",result.getContent()); assertEquals("f",result.getId());
    }
}
