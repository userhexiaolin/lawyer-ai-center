package org.ruoyi.controller.chat;

import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class LawyerMetricsControllerTest {
    @Test void ordinaryUsersCannotReadStatistics(){
        var jdbc=mock(JdbcTemplate.class);
        try(MockedStatic<LoginHelper> login=mockStatic(LoginHelper.class)){
            assertThrows(RuntimeException.class,()->new LawyerMetricsController(jdbc).summary(7));
            verifyNoInteractions(jdbc);
        }
    }
    @Test void rejectsUnknownRangeBeforeQueries(){
        var jdbc=mock(JdbcTemplate.class);
        try(MockedStatic<LoginHelper> login=mockStatic(LoginHelper.class)){
            login.when(LoginHelper::isSuperAdmin).thenReturn(true);
            assertThrows(RuntimeException.class,()->new LawyerMetricsController(jdbc).summary(365));
            verifyNoInteractions(jdbc);
        }
    }
    @Test void adminGetsBeijingCalendarRangeAndRealZeroValues(){
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForMap(anyString(),any(),any(),any())).thenReturn(Map.of("n",0L,"amount",BigDecimal.ZERO));
        try(MockedStatic<LoginHelper> login=mockStatic(LoginHelper.class)){
            login.when(LoginHelper::isSuperAdmin).thenReturn(true);
            login.when(LoginHelper::getTenantId).thenReturn("000000");
            var result=new LawyerMetricsController(jdbc).summary(7).getData();
            var today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
            assertEquals(today.minusDays(6).toString(),result.from());
            assertEquals(today.toString(),result.until());
            assertEquals("Asia/Shanghai",result.timezone());
            assertEquals(0L,result.newUsers());assertEquals(BigDecimal.ZERO,result.paidAmount());
            verify(jdbc).queryForMap(contains("tenant_id=?"),eq("000000"),any(),any());
        }
    }
}
