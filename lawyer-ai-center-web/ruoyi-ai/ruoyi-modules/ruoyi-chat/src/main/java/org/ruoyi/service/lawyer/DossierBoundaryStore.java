package org.ruoyi.service.lawyer;

import org.springframework.stereotype.Component;
import java.util.Optional;

/** Fail closed until a permission-checked, persisted boundary adapter is implemented and tested.
 * No controller or configuration boolean can issue a grant in task 84.
 */
@Component
public class DossierBoundaryStore {
    public Optional<DossierOutboundPolicy.Grant> find(DossierOutboundPolicy.Scope scope) {
        return Optional.empty();
    }
}
