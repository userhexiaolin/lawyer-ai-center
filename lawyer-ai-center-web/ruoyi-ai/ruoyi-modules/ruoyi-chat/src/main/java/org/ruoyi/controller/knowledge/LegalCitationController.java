package org.ruoyi.controller.knowledge;

import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.domain.vo.knowledge.KnowledgeRetrievalVo;
import org.ruoyi.service.knowledge.LegalCitationService;
import org.springframework.web.bind.annotation.*;

/** Read-only citation access uses knowledge ownership/share checks, without admin edit permissions. */
@RestController
@RequestMapping("/legal/citations")
@RequiredArgsConstructor
public class LegalCitationController {
    private final LegalCitationService citations;

    @GetMapping("/fragments/{fragmentId}")
    public R<KnowledgeRetrievalVo> detail(@PathVariable String fragmentId) {
        return R.ok(citations.detail(fragmentId));
    }
}
