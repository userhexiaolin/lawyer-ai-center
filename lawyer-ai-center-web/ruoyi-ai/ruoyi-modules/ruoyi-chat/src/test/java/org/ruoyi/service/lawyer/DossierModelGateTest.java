package org.ruoyi.service.lawyer;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.service.lawyer.CaseAccess;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class DossierModelGateTest {
    private static DossierOutboundPolicy.Scope scope(Long caseId, String fingerprint, String endpoint) {
        return new DossierOutboundPolicy.Scope("000000", caseId, 7L, Set.of(fingerprint),
            DossierOutboundPolicy.Purpose.CLAIMS, 3L, "test-provider", endpoint, "test-region", "o001-v1");
    }
    private static DossierOutboundPolicy.Scope scope() { return scope(42L, "8:" + "a".repeat(64), "https://model.invalid/v1"); }

    @Test void productionStoreNeverInventsConsent() {
        assertTrue(new DossierBoundaryStore().find(scope()).isEmpty());
    }

    @Test void unauthorizedCallCannotConstructOrInvokeProvider() {
        var cases = mock(CaseAccess.class);
        var gate = new DossierModelGate(cases, new DossierBoundaryStore());
        var calls = new AtomicInteger();
        try (var login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getTenantId).thenReturn("000000");
            login.when(LoginHelper::getUserId).thenReturn(7L);
            var error = assertThrows(ServiceException.class, () -> gate.call(scope(), calls::incrementAndGet));
            assertEquals(403, error.getCode());
            assertTrue(error.getMessage().contains("AUTHORIZATION_REQUIRED"));
            assertEquals(0, calls.get());
            verify(cases).owned(42L);
        }
    }

    @Test void identityAndCaseDenialHappenBeforeBoundaryLookupAndProvider() {
        var cases = mock(CaseAccess.class);
        var store = mock(DossierBoundaryStore.class);
        var gate = new DossierModelGate(cases, store);
        var calls = new AtomicInteger();
        try (var login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getTenantId).thenReturn("other-tenant");
            login.when(LoginHelper::getUserId).thenReturn(7L);
            assertThrows(ServiceException.class, () -> gate.call(scope(), calls::incrementAndGet));
            verifyNoInteractions(cases, store);
            login.when(LoginHelper::getTenantId).thenReturn("000000");
            doThrow(new ServiceException("案件不可用", 404)).when(cases).owned(42L);
            assertThrows(ServiceException.class, () -> gate.call(scope(), calls::incrementAndGet));
            verifyNoInteractions(store);
            assertEquals(0, calls.get());
        }
    }

    @Test void allowRequiresExactServerGrantAndRevocationIsCheckedAgain() {
        var store = mock(DossierBoundaryStore.class);
        var gate = new DossierModelGate(mock(CaseAccess.class), store);
        when(store.find(scope())).thenReturn(Optional.of(new DossierOutboundPolicy.Grant(scope(), Instant.now().plusSeconds(60))), Optional.empty());
        var calls = new AtomicInteger();
        try (var login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getTenantId).thenReturn("000000");
            login.when(LoginHelper::getUserId).thenReturn(7L);
            assertEquals(1, gate.call(scope(), calls::incrementAndGet));
            assertThrows(ServiceException.class, () -> gate.call(scope(), calls::incrementAndGet));
            assertEquals(1, calls.get());
        }
    }

    @Test void changedCaseVersionOrDestinationCannotReuseGrant() {
        var time = Instant.parse("2026-10-21T00:00:00Z");
        var grant = new DossierOutboundPolicy.Grant(scope(), time.plusSeconds(60));
        for (var changed : Set.of(scope(43L, "8:" + "a".repeat(64), scope().endpoint()),
            scope(42L, "9:" + "b".repeat(64), scope().endpoint()),
            scope(42L, "8:" + "a".repeat(64), "http://localhost:11434"))) {
            assertEquals(DossierOutboundPolicy.Decision.SCOPE_CHANGED, DossierOutboundPolicy.evaluate(changed, grant, time));
        }
        assertEquals(DossierOutboundPolicy.Decision.AUTHORIZATION_EXPIRED, DossierOutboundPolicy.evaluate(scope(), grant, time.plusSeconds(60)));
        assertEquals(DossierOutboundPolicy.Decision.INVALID_SCOPE, DossierOutboundPolicy.evaluate(scope(42L, "filename.pdf", scope().endpoint()), grant, time));
    }

    @Test void denialAuditContainsOnlyDecisionAndNoSensitiveScopeOrPrompt() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(DossierModelGate.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        var previousLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.INFO);
        String privateValue = "SYNTHETIC_PRIVATE_13800000000";
        var base = scope();
        var sensitive = new DossierOutboundPolicy.Scope(base.tenantId(), base.caseId(), base.userId(),
            base.sourceFingerprints(), base.purpose(), base.modelId(), base.provider(), base.endpoint(), privateValue, base.policyVersion());
        try (var login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getTenantId).thenReturn("000000");
            login.when(LoginHelper::getUserId).thenReturn(7L);
            var gate = new DossierModelGate(mock(CaseAccess.class), new DossierBoundaryStore());
            assertThrows(ServiceException.class, () -> gate.call(sensitive, () -> fail("provider must not execute")));
            assertFalse(appender.list.isEmpty());
            for (var event : appender.list) {
                assertEquals("O001 model boundary decision=AUTHORIZATION_REQUIRED", event.getFormattedMessage());
                assertFalse(event.getFormattedMessage().contains(privateValue));
                assertNull(event.getThrowableProxy());
            }
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }
}
