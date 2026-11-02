package org.ruoyi.service.lawyer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.service.lawyer.CaseAccess;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

/** Task-84 preflight seam, placed before provider construction, storage reads and network calls.
 * O001 workers must reconstruct scopes/identity and re-read grants immediately before each call.
 * This does not retrofit or authorize the legacy general-chat/contract-review routes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DossierModelGate {
    private final CaseAccess cases;
    private final DossierBoundaryStore boundaries;

    public <T> T call(DossierOutboundPolicy.Scope scope, Supplier<T> providerCall) {
        if (scope == null || !Objects.equals(scope.tenantId(), LoginHelper.getTenantId())
            || !Objects.equals(scope.userId(), LoginHelper.getUserId())) {
            log.info("O001 model boundary decision=IDENTITY_DENIED");
            throw new ServiceException("案件不可用", 404);
        }
        try {
            cases.owned(scope.caseId());
        } catch (ServiceException e) {
            log.info("O001 model boundary decision=CASE_ACCESS_DENIED");
            throw e;
        }
        var decision = DossierOutboundPolicy.evaluate(scope, boundaries.find(scope).orElse(null), Instant.now());
        // Deliberately log only an enum, not scope.toString(), source content, URLs or credentials.
        log.info("O001 model boundary decision={}", decision);
        if (decision != DossierOutboundPolicy.Decision.ALLOW) {
            throw new ServiceException("材料模型处理未获授权或授权范围已变化（" + decision.name() + "）", 403);
        }
        return providerCall.get();
    }
}
