package org.ruoyi.service.lawyer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.ruoyi.common.core.exception.ServiceException;
import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class DossierEventDateTest {
    @Test void partialMonthsRemainMonthsWhileDerivedBoundsHandleLeapYears(){var month=DossierEventDate.partial("2024-02");assertEquals("month",month.precision());assertEquals("2024-02-29",month.end().toString());DossierEventDate.validate("approximate","2024年2月前后","2024-02",null,null);assertThrows(ServiceException.class,()->DossierEventDate.validate("exact","2024年2月","2024-02",null,null));assertThrows(ServiceException.class,()->DossierEventDate.partial("2023-02-29"));}
    @Test void rangesCrossYearsAndUnknownRelativeNeverAcquireGuessedDates(){DossierEventDate.validate("range","年末至次年初","2024-12-20","2025-01-10","Asia/Shanghai");DossierEventDate.validate("unknown","某日",null,null,null);DossierEventDate.validate("relative","同年四季度",null,null,null);assertThrows(ServiceException.class,()->DossierEventDate.validate("relative","次日","2025-01-01",null,null));assertThrows(ServiceException.class,()->DossierEventDate.validate("range","倒置","2025-01-01","2024-01-01",null));assertThrows(ServiceException.class,()->DossierEventDate.validate("exact","日期","2025-01-01",null,"Invalid/Zone"));}
}
