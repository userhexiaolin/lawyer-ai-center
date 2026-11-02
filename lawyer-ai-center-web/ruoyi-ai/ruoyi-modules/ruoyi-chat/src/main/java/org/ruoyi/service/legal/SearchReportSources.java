package org.ruoyi.service.legal;

import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.domain.bo.legal.SearchReportInput.AddItem;
import org.ruoyi.service.knowledge.LegalCitationService;
import org.ruoyi.service.knowledge.LegalMetadataService;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SearchReportSources {
    private final LegalCitationService fragments;
    private static final Set<String> TYPES = Set.of("case_full","case_section","law","viewpoint","custom_text","chart");
    private static final Set<String> STATUSES = Set.of("effective","amended","repealed","unknown");
    public record Snapshot(String type, String refId, String sectionKey, String title, String content,
        String lawStatus, String lawName, String articleNo, String sourceUrl, String lawKey) {}
    public Snapshot resolve(AddItem input) {
        if (input == null || input.itemType() == null || !TYPES.contains(input.itemType())) throw bad("条目类型无效");
        String title = input.snapshotTitle(), content = input.snapshotContent(), status = input.lawStatus();
        String lawName = input.lawName(), articleNo = input.articleNo(), source = input.sourceUrl(), ref = input.refId();
        if (has(input.fragmentId())) {
            // Capture the permitted original NOW; later generation never consults the live fragment.
            var fragment = fragments.detail(input.fragmentId());
            title = fragment.getSourceName(); content = fragment.getContent(); ref = fragment.getId();
            lawName = null; articleNo = null; source = null; status = null;
            var citation = fragment.getReference();
            if (citation != null) {
                source = citation.sourceUrl();
                if ("law".equals(citation.type())) {
                    lawName = citation.lawName(); articleNo = citation.articleNo(); status = citation.status();
                    title = "《" + lawName.replace("《", "").replace("》", "") + "》" + articleNo;
                } else if ("case".equals(citation.type())) {
                    title = citation.caseNo() + (has(citation.title()) ? " · " + citation.title() : "");
                }
            } else if (fragment.getLegalMetadata() != null && "law".equals(input.itemType())) {
                var legal = fragment.getLegalMetadata(); lawName = legal.lawName(); articleNo = legal.articleNo();
                status = LegalMetadataService.canonicalStatus(legal);
            }
            if (!has(title)) title = "知识库片段 " + ref;
        }
        requireText(title, 500, "请提供条目标题（最长500字）");
        requireText(content, 100000, "请提供文本快照（最长100000字）");
        bounded(ref, 200, "来源标识过长"); bounded(input.sectionKey(), 100, "段落标识过长");
        bounded(lawName, 300, "法规名称过长"); bounded(articleNo, 100, "条款号过长");
        bounded(source, 2000, "来源地址过长");
        if (has(source) && !source.matches("https?://[^\\s]+")) throw bad("来源地址仅支持 HTTP(S)");
        String lawKey = null;
        if ("law".equals(input.itemType())) {
            status = has(status) ? status : "unknown";
            if (!STATUSES.contains(status)) throw bad("法规时效状态无效");
            if (has(lawName)) {
                lawName = Normalizer.normalize(lawName, Normalizer.Form.NFKC).replace("《", "").replace("》", "").strip();
                requireText(lawName, 300, "法规名称不能为空且不得超过300字");
            }
            String articleKey = null;
            if (has(articleNo)) {
                articleNo = Normalizer.normalize(articleNo, Normalizer.Form.NFKC).replaceAll("[\\s\\u3000]+", "");
                articleKey = articleNo;
                if (articleNo.matches("第?[0-9零〇一二三四五六七八九十百千万两]+条?")) {
                    articleKey = articleNo.replaceFirst("^第", "").replaceFirst("条$", "");
                    articleNo = "第" + articleKey + "条";
                }
                requireText(articleNo, 100, "条款号不能为空且不得超过100字");
            }
            if (has(lawName) && has(articleKey)) {
                lawKey = hash(lawName.replaceAll("[\\s\\u3000]+", "") + "\u0000" + articleKey);
            }
        } else status = null;
        return new Snapshot(input.itemType(), emptyToNull(ref), emptyToNull(input.sectionKey()), title.strip(), content,
            status, emptyToNull(lawName), emptyToNull(articleNo), emptyToNull(source), lawKey);
    }
    public static boolean has(String value) { return value != null && !value.isBlank(); }
    public static ServiceException bad(String message) { return new ServiceException(message, 400); }
    public static void requireText(String value, int max, String message) {
        if (!has(value)) throw bad(message); bounded(value, max, message);
    }
    public static void bounded(String value, int max, String message) {
        if (value == null) return;
        if (value.length() > max) throw bad(message);
        // DOCX is XML 1.0. Reject unsupported characters rather than silently alter snapshots.
        if (value.codePoints().anyMatch(c -> !(c == 9 || c == 10 || c == 13 || c >= 32 && c <= 0xD7FF || c >= 0xE000 && c <= 0xFFFD || c >= 0x10000 && c <= 0x10FFFF)))
            throw bad("文本快照含 Word 不支持的控制字符或无效字符");
    }
    private static String emptyToNull(String value) { return has(value) ? value : null; }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
