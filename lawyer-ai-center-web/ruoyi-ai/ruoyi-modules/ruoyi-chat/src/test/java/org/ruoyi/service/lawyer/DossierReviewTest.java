package org.ruoyi.service.lawyer;

import org.junit.jupiter.api.*;
import org.ruoyi.common.core.exception.ServiceException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class DossierReviewTest {
    @Test void dateUncertaintyDoesNotInventAnExactDay(){DossierReviewDate.validate(Map.of("datePrecision","unknown"),true);DossierReviewDate.validate(Map.of("datePrecision","range","dateRaw","2025年3月","dateStart","2025-03-01","dateEnd","2025-03-31"),true);DossierReviewDate.validate(Map.of("dateRaw","2025-03-01"),true);assertThrows(ServiceException.class,()->DossierReviewDate.validate(Map.of("dateRaw","2025-03"),true));assertThrows(ServiceException.class,()->DossierReviewDate.validate(Map.of("datePrecision","unknown","dateStart","2025-03-01"),true));assertThrows(ServiceException.class,()->DossierReviewDate.validate(Map.of("datePrecision","range","dateRaw","March","dateStart","2025-03-31","dateEnd","2025-03-01"),true));assertThrows(ServiceException.class,()->DossierReviewDate.validate(Map.of("datePrecision","range","dateRaw","February","dateStart","2025-02-30","dateEnd","2025-03-01"),true));}
    @Test void auditDiffKeepsFieldsAndMaskedNamesWithProtectedReferences(){String before="{\"claim\":{\"version\":1,\"valuesJson\":\"{\\\"name\\\":\\\"SyntheticPartyAlpha\\\",\\\"amountRaw\\\":\\\"500000.00\\\"}\"}}",after="{\"claim\":{\"version\":2,\"valuesJson\":\"{\\\"name\\\":\\\"SyntheticPartyBeta\\\",\\\"amountRaw\\\":\\\"50000.00\\\"}\"}}";var fields=DossierReviewAudit.diff(before,after,"claim/7/audit/9");var name=fields.stream().filter(f->f.field().equals("claim.values.name")).findFirst().orElseThrow();assertTrue(name.sensitive());assertFalse(name.oldValue().contains("SyntheticPartyAlpha"));assertTrue(name.protectedRef().startsWith("claim/7/audit/9#"));var amount=fields.stream().filter(f->f.field().endsWith("amountRaw")).findFirst().orElseThrow();assertEquals("500000.00",amount.oldValue());assertEquals("50000.00",amount.newValue());assertNotEquals(DossierReviewAudit.fingerprint(before),DossierReviewAudit.fingerprint(after));}
    @Test void auditFingerprintsAreCanonicalAndHistoryCannotSilentlyDisappear(){assertEquals(DossierReviewAudit.fingerprint("{\"b\":2,\"a\":1}"),DossierReviewAudit.fingerprint("{\"a\":1,\"b\":2}"));assertThrows(ServiceException.class,()->DossierReviewAudit.diff("corrupt","{}","ref"));}
}
