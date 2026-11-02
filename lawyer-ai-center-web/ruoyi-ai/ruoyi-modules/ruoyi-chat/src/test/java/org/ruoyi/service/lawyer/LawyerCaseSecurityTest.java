package org.ruoyi.service.lawyer;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.service.OssService;
import org.ruoyi.domain.bo.lawyer.CaseInput;
import org.ruoyi.domain.entity.lawyer.LawyerCase;
import org.ruoyi.mapper.lawyer.LawyerCaseMapper;
import org.ruoyi.mapper.chat.ChatSessionMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("dev")
class LawyerCaseSecurityTest {
    @BeforeAll static void mapperMetadata() {
        var builder=new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),"case-tests");
        builder.setCurrentNamespace("case-tests");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(builder,LawyerCase.class);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(builder,org.ruoyi.domain.entity.chat.ChatSession.class);
    }
    @Test void creationBindsServerOwnerAndDoesNotReuseClientPrimaryKey() {
        var mapper=mock(LawyerCaseMapper.class);var service=new LawyerCaseService(mapper,mock(CaseAccess.class),mock(ChatSessionMapper.class),mock(JdbcTemplate.class),mock(OssService.class));
        when(mapper.insert(any(LawyerCase.class))).thenAnswer(call->{LawyerCase value=call.getArgument(0);assertNull(value.getId());assertEquals(7L,value.getUserId());assertEquals("000000",value.getTenantId());value.setId(42L);return 1;});
        try(MockedStatic<LoginHelper> login=mockStatic(LoginHelper.class)){
            login.when(LoginHelper::getUserId).thenReturn(7L);login.when(LoginHelper::getTenantId).thenReturn("000000");
            assertEquals(42L,service.create(new CaseInput(999L," 测试案件 ",null,null,null,null,null,null,null,null,null)));
        }
    }
    @Test void ownershipDenialPreventsAllDeleteSideEffects() {
        var mapper=mock(LawyerCaseMapper.class);var access=mock(CaseAccess.class);var sessions=mock(ChatSessionMapper.class);var jdbc=mock(JdbcTemplate.class);var oss=mock(OssService.class);
        doThrow(new ServiceException("案件不可用",404)).when(access).owned(99L,true);
        var service=new LawyerCaseService(mapper,access,sessions,jdbc,oss);
        assertThrows(ServiceException.class,()->service.delete(99L));verifyNoInteractions(mapper,sessions,jdbc,oss);
    }
    @Test void deleteUnlinksSessionsAndFilesButNeverDeletesMessagesOrRawObjects() {
        var mapper=mock(LawyerCaseMapper.class);var access=mock(CaseAccess.class);var sessions=mock(ChatSessionMapper.class);var jdbc=mock(JdbcTemplate.class);var oss=mock(OssService.class);
        var value=new LawyerCase();value.setId(99L);value.setUserId(7L);value.setTenantId("000000");when(access.owned(99L,true)).thenReturn(value);
        new LawyerCaseService(mapper,access,sessions,jdbc,oss).delete(99L);
        verify(sessions).update(isNull(),any());verify(jdbc).update(contains("lawyer_case_file"),eq("000000"),eq(7L),eq(99L));verify(mapper).deleteById(99L);verifyNoInteractions(oss);
    }
    @Test void inaccessibleAndMissingCasesUseSameNonRevealingError() {
        var mapper=mock(LawyerCaseMapper.class);var access=new CaseAccess(mapper);
        try(MockedStatic<LoginHelper> login=mockStatic(LoginHelper.class)){
            login.when(LoginHelper::getUserId).thenReturn(7L);login.when(LoginHelper::getTenantId).thenReturn("000000");
            var error=assertThrows(ServiceException.class,()->access.owned(123L));assertEquals(404,error.getCode());assertEquals("案件不可用",error.getMessage());
            verify(mapper).selectOne(argThat(wrapper -> wrapper.getSqlSegment().contains("user_id") && wrapper.getSqlSegment().contains("tenant_id")));
        }
    }
    @Test void foreignFileAndSessionCannotBeLinkedToAnOwnedCase() {
        var mapper=mock(LawyerCaseMapper.class);var access=mock(CaseAccess.class);var sessions=mock(ChatSessionMapper.class);var jdbc=mock(JdbcTemplate.class);var oss=mock(OssService.class);
        var value=new LawyerCase();value.setId(99L);value.setUserId(7L);value.setTenantId("000000");when(access.owned(99L,true)).thenReturn(value);
        when(jdbc.queryForObject(anyString(),eq(Long.class),any(),any(),any())).thenReturn(0L);
        var service=new LawyerCaseService(mapper,access,sessions,jdbc,oss);
        try(MockedStatic<LoginHelper> login=mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(7L);
            assertThrows(ServiceException.class,()->service.linkFile(99L,123L));
            assertThrows(ServiceException.class,()->service.linkSession(99L,123L));
            verify(sessions,never()).update(any(),any());verify(jdbc,never()).update(anyString(),any(),any(),any(),any());
        }
    }
}
