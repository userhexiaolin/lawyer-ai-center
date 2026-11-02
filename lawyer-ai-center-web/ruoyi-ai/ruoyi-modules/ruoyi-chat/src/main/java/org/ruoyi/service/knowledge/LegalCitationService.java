package org.ruoyi.service.knowledge;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.domain.entity.knowledge.KnowledgeFragment;
import org.ruoyi.domain.vo.knowledge.KnowledgeRetrievalVo;
import org.ruoyi.mapper.knowledge.KnowledgeFragmentMapper;
import org.springframework.stereotype.Service;
import java.util.List;

/** Shared, permission-checked fragment reader for citation details and snapshot capture. */
@Service
@RequiredArgsConstructor
public class LegalCitationService {
    private final KnowledgeAccess access;
    private final KnowledgeFragmentMapper fragments;
    private final LegalMetadataService metadata;
    public KnowledgeRetrievalVo detail(String fragmentId) {
        var allowed = access.readableIds();
        if (fragmentId == null || fragmentId.isBlank() || allowed.isEmpty()) throw new ServiceException("原文不存在或无访问权限", 403);
        var query = Wrappers.<KnowledgeFragment>lambdaQuery().in(KnowledgeFragment::getKnowledgeId, allowed);
        query.and(q -> {
            q.eq(KnowledgeFragment::getFid, fragmentId);
            try { if (fragmentId.matches("[1-9]\\d{0,18}")) { Long id = Long.valueOf(fragmentId); q.or().eq(KnowledgeFragment::getId, id); } }
            catch (NumberFormatException ignored) { /* Oversized numeric fids are not database IDs. */ }
        });
        var rows = fragments.selectList(query);
        if (rows.size() != 1) throw new ServiceException("原文不存在或无访问权限", 403);
        var fragment = rows.get(0);
        access.read(fragment.getKnowledgeId());
        var result = KnowledgeRetrievalVo.builder().id(fragmentId).docId(fragment.getDocId())
            .knowledgeId(fragment.getKnowledgeId()).content(fragment.getContent()).idx(fragment.getIdx()).build();
        return metadata.enrich(List.of(result)).get(0);
    }
}
