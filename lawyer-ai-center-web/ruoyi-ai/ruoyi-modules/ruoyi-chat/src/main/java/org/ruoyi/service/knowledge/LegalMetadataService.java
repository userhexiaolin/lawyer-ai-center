package org.ruoyi.service.knowledge;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.ruoyi.domain.entity.knowledge.KnowledgeAttach;
import org.ruoyi.domain.vo.knowledge.KnowledgeRetrievalVo;
import org.ruoyi.mapper.knowledge.KnowledgeAttachMapper;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LegalMetadataService {
    private final KnowledgeAttachMapper mapper;
    public record LegalMetadata(String lawName, String articleNo, String region, String validityStatus,
        LocalDate publishDate, LocalDate expireDate, String sourceVersion) {
        public String citation() {
            if (lawName == null || lawName.isBlank()) return "";
            String state = validityStatus == null || validityStatus.isBlank() ? "时效待核实" : validityStatus;
            if (expireDate != null && !expireDate.isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai")))) state = "已失效";
            String article = articleNo == null ? "" : articleNo;
            if (!article.isBlank() && !article.startsWith("第")) article = "第" + article + (article.endsWith("条") ? "" : "条");
            return "《" + lawName.replace("《", "").replace("》", "") + "》" + article + "（" + state + "）";
        }
    }
    public static String canonicalStatus(LegalMetadata legal) {
        if (legal.expireDate() != null && !legal.expireDate().isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai")))) return "repealed";
        return switch (Objects.toString(legal.validityStatus(), "")) {
            case "有效", "effective" -> "effective";
            case "已修订", "已修改", "amended" -> "amended";
            case "已失效", "repealed" -> "repealed";
            default -> "unknown";
        };
    }
    public List<KnowledgeRetrievalVo> enrich(List<KnowledgeRetrievalVo> results) {
        var ids = results.stream().map(KnowledgeRetrievalVo::getDocId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return results;
        // Match both knowledge and doc identity; tenant filtering remains in the existing mapper.
        var attachments = mapper.selectList(Wrappers.<KnowledgeAttach>lambdaQuery().in(KnowledgeAttach::getDocId, ids));
        Map<String, KnowledgeAttach> byDocument = new HashMap<>();
        for (var attach : attachments) byDocument.put(attach.getKnowledgeId() + ":" + attach.getDocId(), attach);
        for (var result : results) {
            var attach = byDocument.get(result.getKnowledgeId() + ":" + result.getDocId());
            if (attach == null) continue;
            result.setSourceName(attach.getName());
            result.setLegalMetadata(new LegalMetadata(attach.getLawName(), attach.getArticleNo(), attach.getRegion(), attach.getValidityStatus(), attach.getPublishDate(), attach.getExpireDate(), attach.getSourceVersion()));
            var legal = result.getLegalMetadata();
            String caseNo = attach.getCaseNo();
            if (caseNo != null && !caseNo.isBlank()) {
                result.setReference(new org.ruoyi.domain.vo.knowledge.LegalReference("case", null, null, null,
                    caseNo, attach.getName(), attach.getCourt(), attach.getSourceUrl(), result.getId()));
            } else if (attach.getLawName() != null && !attach.getLawName().isBlank()
                && attach.getArticleNo() != null && !attach.getArticleNo().isBlank()) {
                result.setReference(new org.ruoyi.domain.vo.knowledge.LegalReference("law", attach.getLawName(), attach.getArticleNo(),
                    canonicalStatus(legal), null, null, null, attach.getSourceUrl(), result.getId()));
            }
        }
        return results;
    }
}
