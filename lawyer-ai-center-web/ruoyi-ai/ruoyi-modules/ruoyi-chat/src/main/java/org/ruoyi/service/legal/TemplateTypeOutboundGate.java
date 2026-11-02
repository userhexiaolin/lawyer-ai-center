package org.ruoyi.service.legal;

import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** Server policy solely for classification of user-selected administrative templates.
 * On 2026-10-24 the user explicitly approved this verified endpoint/model and at most
 * 6000 characters per selected file, then requested automatic background recognition.
 * No HTTP field can choose a destination or issue a grant; O001 case grants are unaffected.
 */
@Component
public class TemplateTypeOutboundGate {
    private final Long approvedModelId;
    private final String approvedModel;
    private final String approvedProvider;
    private final String approvedEndpoint;

    public TemplateTypeOutboundGate(
        @Value("${legal.template-type-classification.approved-model-id:2110631969010016257}") Long modelId,
        @Value("${legal.template-type-classification.approved-model:/models/Qwen/Qwen3.6-35B-A3B-FP8}") String model,
        @Value("${legal.template-type-classification.approved-provider:vllm}") String provider,
        @Value("${legal.template-type-classification.approved-endpoint:http://192.168.110.19:8000/v1}") String endpoint) {
        approvedModelId = modelId; approvedModel = model; approvedProvider = provider; approvedEndpoint = normalize(endpoint);
    }

    public record Grant(String tenant, Long user, Long modelId, String model, String provider,
                        String endpoint, String digest, Instant expiresAt) {}

    public void requireTarget(ChatModelVo config) {
        if (config == null || !Objects.equals(approvedModelId, config.getId())
            || !Objects.equals(approvedModel, config.getModelName())
            || !Objects.equals(approvedProvider, config.getProviderCode())
            || !"chat".equals(config.getCategory()) || approvedEndpoint.isBlank()
            || !approvedEndpoint.equals(normalize(config.getApiHost())))
            throw new DraftingException("AI类型识别目标未获准出授权，保持当前类型，可直接上传", 403);
    }

    public Grant authorize(ChatModelVo config, String excerpt) {
        requireTarget(config);
        if (LoginHelper.getUserId() == null || LoginHelper.getTenantId() == null || LoginHelper.getTenantId().isBlank())
            throw new DraftingException("请先登录", 401);
        if (excerpt == null || excerpt.isBlank() || excerpt.length() > 6000) throw new IllegalArgumentException("Invalid classification excerpt");
        return new Grant(LoginHelper.getTenantId(), LoginHelper.getUserId(), config.getId(), config.getModelName(),
            config.getProviderCode(), normalize(config.getApiHost()), digest(excerpt), Instant.now().plusSeconds(90));
    }

    /** Workers use the server-issued identity snapshot rather than inheriting login ThreadLocals. */
    public <T> T call(Grant grant, ChatModelVo config, String excerpt, Supplier<T> providerCall) {
        requireTarget(config);
        if (grant == null || grant.tenant() == null || grant.tenant().isBlank() || grant.user() == null
            || grant.expiresAt() == null || !grant.expiresAt().isAfter(Instant.now())
            || !Objects.equals(grant.modelId(), config.getId()) || !Objects.equals(grant.model(), config.getModelName())
            || !Objects.equals(grant.provider(), config.getProviderCode()) || !Objects.equals(grant.endpoint(), normalize(config.getApiHost()))
            || excerpt == null || excerpt.isBlank() || excerpt.length() > 6000 || !Objects.equals(grant.digest(), digest(excerpt)))
            throw new DraftingException("AI类型识别授权已失效，保持当前类型，可直接上传", 403);
        return providerCall.get();
    }

    private static String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            var uri = URI.create(value.strip());
            if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) return "";
            return uri.toString().replaceAll("/+$", "");
        } catch (IllegalArgumentException e) { return ""; }
    }
}
