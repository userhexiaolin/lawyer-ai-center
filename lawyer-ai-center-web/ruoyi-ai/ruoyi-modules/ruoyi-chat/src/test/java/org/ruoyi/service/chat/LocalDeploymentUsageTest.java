package org.ruoyi.service.chat;

import org.junit.jupiter.api.*;
import org.ruoyi.service.billing.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("dev")
class LocalDeploymentUsageTest {
    @Test void zeroCreditSessionDoesNotTouchUsageOrBillingLedger() {
        var jdbc=mock(JdbcTemplate.class);
        var service=new LawyerUsageService(jdbc);
        var billing=mock(LawyerBillingService.class); service.setBilling(billing);
        when(jdbc.queryForList(anyString(),eq(9L),eq(7L),eq("000000")))
            .thenReturn(java.util.List.of(java.util.Map.of("usage_started",0,"usage_kind","REVIEW")));
        assertDoesNotThrow(()->service.beginProcessing("000000",7L,9L));
        verify(jdbc,never()).update(anyString(),any(Object[].class));
        verifyNoInteractions(billing);
    }
    @Test void disabledDirectEntitlementNeverStartsTransaction() {
        var jdbc=mock(JdbcTemplate.class); var tx=mock(TransactionTemplate.class);
        var gateway=mock(AlipayGateway.class);
        var service=new LawyerBillingService(jdbc,tx,new BillingProperties(),gateway);
        assertTrue(service.consumeEntitlement("000000",7L,"CHAT"));
        assertTrue(service.consumeEntitlement("000000",7L,"REVIEW"));
        verifyNoInteractions(jdbc,tx,gateway);
    }
    @Test void localModeStillRejectsInvalidSessionOwnership() {
        var jdbc=mock(JdbcTemplate.class); var service=new LawyerUsageService(jdbc);
        assertThrows(org.ruoyi.common.core.exception.ServiceException.class,()->service.beginProcessing("000000",7L,9L));
    }
}
