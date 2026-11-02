package org.ruoyi.service.lawyer;

import java.net.URI;
import java.time.Instant;
import java.util.Set;

/** Pure policy. Scopes and grants must be assembled by trusted server code, never from consent DTOs. */
public final class DossierOutboundPolicy {
    private DossierOutboundPolicy() {}

    public enum Purpose { OCR, CLAIMS, BANK_ROWS, TIMELINE, EVIDENCE, RELATIONS, DRAFT, QA }
    public enum Decision { ALLOW, INVALID_SCOPE, AUTHORIZATION_REQUIRED, AUTHORIZATION_EXPIRED, SCOPE_CHANGED }

    /** Source fingerprints are immutable file-version IDs plus SHA-256, not filenames or signed URLs. */
    public record Scope(String tenantId, Long caseId, Long userId, Set<String> sourceFingerprints,
                        Purpose purpose, Long modelId, String provider, String endpoint,
                        String region, String policyVersion) {
        public Scope {
            sourceFingerprints = sourceFingerprints == null ? Set.of() : Set.copyOf(sourceFingerprints);
        }

        boolean valid() {
            if (blank(tenantId) || caseId == null || caseId <= 0 || userId == null || userId <= 0
                || sourceFingerprints.isEmpty() || purpose == null || modelId == null || modelId <= 0
                || blank(provider) || blank(endpoint) || blank(region) || blank(policyVersion)) return false;
            if (sourceFingerprints.stream().anyMatch(s -> !s.matches("[1-9][0-9]*:[a-f0-9]{64}"))) return false;
            try {
                URI uri = URI.create(endpoint);
                // Internal HTTP also requires an explicit grant; host spelling never implies trust.
                return Set.of("https", "http").contains(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null;
            } catch (IllegalArgumentException e) { return false; }
        }

        private static boolean blank(String value) { return value == null || value.isBlank(); }
    }

    /** A future persisted, revocable authorization; null or absent always denies. */
    public record Grant(Scope scope, Instant expiresAt) {}

    public static Decision evaluate(Scope request, Grant grant, Instant now) {
        if (request == null || !request.valid() || now == null) return Decision.INVALID_SCOPE;
        if (grant == null) return Decision.AUTHORIZATION_REQUIRED;
        if (grant.expiresAt() == null || !grant.expiresAt().isAfter(now)) return Decision.AUTHORIZATION_EXPIRED;
        if (!request.equals(grant.scope())) return Decision.SCOPE_CHANGED;
        return Decision.ALLOW;
    }
}
