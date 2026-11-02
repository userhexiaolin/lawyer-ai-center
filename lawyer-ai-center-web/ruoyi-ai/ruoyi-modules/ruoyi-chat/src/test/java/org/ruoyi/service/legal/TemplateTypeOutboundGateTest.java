package org.ruoyi.service.legal;

import org.junit.jupiter.api.*;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.satoken.utils.LoginHelper;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class TemplateTypeOutboundGateTest {
    private final TemplateTypeOutboundGate gate = new TemplateTypeOutboundGate(7L, "test-qwen", "vllm", "http://127.0.0.1:9000/v1");
    private ChatModelVo model() {
        var model = new ChatModelVo(); model.setId(7L); model.setModelName("test-qwen"); model.setProviderCode("vllm");
        model.setApiHost("http://127.0.0.1:9000/v1"); model.setCategory("chat"); return model;
    }

    @Test void allowsOnlyTheAuthorizedDestinationAndExactExcerpt() {
        var config = model();
        try (var login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(3L); login.when(LoginHelper::getTenantId).thenReturn("tenant-test");
            var grant = gate.authorize(config, "synthetic template excerpt");
            assertEquals("ok", gate.call(grant, config, "synthetic template excerpt", () -> "ok"));
            var called = new AtomicBoolean();
            assertThrows(DraftingException.class, () -> gate.call(grant, config, "different content", () -> called.getAndSet(true)));
            assertFalse(called.get());
        }
    }

    @Test void rejectsMissingIdentityOversizedPayloadExpiredGrantAndTargetChangesBeforeCallingProvider() {
        var config = model(); var called = new AtomicBoolean();
        try (var login = mockStatic(LoginHelper.class)) {
            assertThrows(DraftingException.class, () -> gate.authorize(config, "text"));
            login.when(LoginHelper::getUserId).thenReturn(3L); login.when(LoginHelper::getTenantId).thenReturn("tenant-test");
            assertThrows(IllegalArgumentException.class, () -> gate.authorize(config, "x".repeat(6001)));
            var grant = gate.authorize(config, "text");
            var expired = new TemplateTypeOutboundGate.Grant(grant.tenant(), grant.user(), grant.modelId(), grant.model(),
                grant.provider(), grant.endpoint(), grant.digest(), Instant.now().minusSeconds(1));
            assertThrows(DraftingException.class, () -> gate.call(expired, config, "text", () -> called.getAndSet(true)));
            config.setApiHost("http://127.0.0.1:9001/v1");
            assertThrows(DraftingException.class, () -> gate.call(grant, config, "text", () -> called.getAndSet(true)));
            assertFalse(called.get());
        }
    }

    @Test void rejectsUnknownModelProviderAndCredentialBearingOrUnapprovedEndpoints() {
        for (String endpoint : new String[]{"", "https://example.invalid/v1", "http://name:secret@127.0.0.1:9000/v1", "http://127.0.0.1:9000/v1?key=secret"}) {
            var config = model(); config.setApiHost(endpoint); assertThrows(DraftingException.class, () -> gate.requireTarget(config));
        }
        var wrongId = model(); wrongId.setId(8L); assertThrows(DraftingException.class, () -> gate.requireTarget(wrongId));
        var wrongName = model(); wrongName.setModelName("other-model"); assertThrows(DraftingException.class, () -> gate.requireTarget(wrongName));
        var wrongProvider = model(); wrongProvider.setProviderCode("other"); assertThrows(DraftingException.class, () -> gate.requireTarget(wrongProvider));
        assertThrows(DraftingException.class, () -> new TemplateTypeOutboundGate(7L, "test-qwen", "vllm", "").requireTarget(model()));
    }
}
