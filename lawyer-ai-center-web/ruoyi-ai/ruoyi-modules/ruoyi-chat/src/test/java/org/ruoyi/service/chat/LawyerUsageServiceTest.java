package org.ruoyi.service.chat;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.core.exception.ServiceException;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("dev")
class LawyerUsageServiceTest {
    private LawyerUsageService online(JdbcTemplate jdbc) { var service=new LawyerUsageService(jdbc); var properties=new org.ruoyi.service.billing.BillingProperties(); properties.setEnabled(true); service.setBillingProperties(properties); return service; }
    @Test void alreadyStartedSessionDoesNotDebitAgain() {
        var jdbc=mock(JdbcTemplate.class); var service=online(jdbc);
        when(jdbc.queryForList(anyString(), eq(9L), eq(7L), eq("000000"))).thenReturn(java.util.List.of(java.util.Map.of("usage_started",1,"usage_kind","CHAT")));
        service.beginProcessing("000000",7L,9L);
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void firstMessageDebitsAndMarksServerOwnedSession() {
        var jdbc=mock(JdbcTemplate.class); var service=online(jdbc);
        when(jdbc.queryForList(anyString(), eq(9L), eq(7L), eq("000000"))).thenReturn(java.util.List.of(java.util.Map.of("usage_started",0,"usage_kind","REVIEW")));
        when(jdbc.update(startsWith("UPDATE lawyer_daily_usage"),any(),any(),any())).thenReturn(1);
        service.beginProcessing("000000",7L,9L);
        verify(jdbc).update(startsWith("UPDATE chat_session SET usage_started=1"),eq(9L),eq(7L));
    }
    @Test void invalidOwnerOrExhaustionNeverMarksStarted() {
        var jdbc=mock(JdbcTemplate.class); var service=online(jdbc);
        assertThrows(ServiceException.class,()->service.beginProcessing("000000",7L,9L));
        when(jdbc.queryForList(anyString(), eq(9L), eq(7L), eq("000000"))).thenReturn(java.util.List.of(java.util.Map.of("usage_started",0,"usage_kind","CHAT")));
        assertEquals(429,assertThrows(ServiceException.class,()->service.beginProcessing("000000",7L,9L)).getCode());
        verify(jdbc,never()).update(startsWith("UPDATE chat_session"),any(),any());
    }
    @Test void successConsumesAtomicallyForCurrentOwner() {
        var jdbc = mock(JdbcTemplate.class); var service = online(jdbc);
        when(jdbc.update(startsWith("UPDATE"), any(), any(), any())).thenReturn(1);
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L); login.when(LoginHelper::getTenantId).thenReturn("000000");
            assertDoesNotThrow(() -> service.consumeSession(7L));
            verify(jdbc).update(contains("used_sessions < session_limit"), eq("000000"), eq(7L), any());
        }
    }
    @Test void exhaustedQuotaReturnsBusiness429() {
        var service = online(mock(JdbcTemplate.class));
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L); login.when(LoginHelper::getTenantId).thenReturn("000000");
            var error = assertThrows(ServiceException.class, () -> service.consumeSession(7L));
            assertEquals(429, error.getCode()); assertEquals(LawyerUsageService.EXHAUSTED, error.getMessage());
        }
    }
    @Test void anotherOwnerIsRejectedBeforeDatabaseAccess() {
        var jdbc = mock(JdbcTemplate.class); var service = online(jdbc);
        try (MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            assertThrows(ServiceException.class, () -> service.consumeSession(8L)); verifyNoInteractions(jdbc);
        }
    }
}
