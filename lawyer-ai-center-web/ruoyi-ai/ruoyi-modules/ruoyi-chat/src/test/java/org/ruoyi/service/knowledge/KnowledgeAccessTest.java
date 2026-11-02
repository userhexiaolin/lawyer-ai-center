package org.ruoyi.service.knowledge;

import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class KnowledgeAccessTest {
    @Test void anonymousAccessNeverQueriesDatabase(){
        var jdbc=mock(JdbcTemplate.class);
        try(MockedStatic<LoginHelper> login=mockStatic(LoginHelper.class)){
            assertThrows(RuntimeException.class,()->new KnowledgeAccess(jdbc).read(1L));
            verifyNoInteractions(jdbc);
        }
    }
    @Test void publicReadPermissionDoesNotAuthorizeModification(){
        var jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(),eq(Long.class),any(),any(),any(),any())).thenAnswer(call->call.<String>getArgument(0).contains("share=1")?1L:0L);
        try(MockedStatic<LoginHelper> login=mockStatic(LoginHelper.class)){
            login.when(LoginHelper::getTenantId).thenReturn("000000");
            login.when(LoginHelper::getUserId).thenReturn(7L);
            var access=new KnowledgeAccess(jdbc);
            assertDoesNotThrow(()->access.read(12L));
            assertThrows(RuntimeException.class,()->access.write(12L));
            verify(jdbc).queryForObject(contains("OR share=1"),eq(Long.class),eq(12L),eq("000000"),eq(false),eq(7L));
        }
    }
}
