package org.ruoyi.service.lawyer;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.MockedStatic;
import org.ruoyi.common.core.domain.dto.OssDTO;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.service.OssService;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.bo.lawyer.CaseTaskInput.*;
import org.ruoyi.domain.entity.lawyer.LawyerCase;
import org.ruoyi.mapper.lawyer.LawyerCaseMapper;
import org.ruoyi.mapper.chat.ChatSessionMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Only connection-local TEMPORARY tables are used. Existing case/file data is never touched. */
@Tag("dev") @EnabledIfSystemProperty(named="case.tasks.mysql",matches="true")
class CaseTaskMysqlTest {
    SingleConnectionDataSource source;JdbcTemplate jdbc;TransactionTemplate tx;CaseTaskService tasks;
    LawyerCaseService materials;OssService oss;MockedStatic<LoginHelper> login;
    long user=101L;String tenant="000000";
    static String setting(String text,String field) {
        var matcher=Pattern.compile("(?m)^          "+field+": (.+)$").matcher(text);
        if(!matcher.find())throw new IllegalStateException("Missing datasource configuration");return matcher.group(1).strip();
    }
    @BeforeEach void setup() throws Exception {
        var root=Path.of(System.getProperty("case.tasks.workspace"));var config=Files.readString(root.resolve("ruoyi-ai/ruoyi-admin/src/main/resources/application-dev.yml"));
        source=new SingleConnectionDataSource(DriverManager.getConnection(setting(config,"url"),setting(config,"username"),setting(config,"password")),true);
        jdbc=new JdbcTemplate(source);tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        var migration=Files.readString(root.resolve("ruoyi-ai/docs/script/sql/update/2026-10-17-case-tasks.sql")).replaceAll("(?m)--.*$","").replace("CREATE TABLE IF NOT EXISTS","CREATE TEMPORARY TABLE IF NOT EXISTS");
        for(String sql:migration.split(";"))if(!sql.isBlank())jdbc.execute(sql);
        jdbc.execute("CREATE TEMPORARY TABLE lawyer_case(id BIGINT PRIMARY KEY,tenant_id VARCHAR(20),user_id BIGINT,deleted INT DEFAULT 0)");
        jdbc.execute("INSERT INTO lawyer_case(id,tenant_id,user_id) VALUES(11,'000000',101),(22,'000000',202),(33,'111111',101)");
        jdbc.execute("CREATE TEMPORARY TABLE lawyer_case_file(tenant_id VARCHAR(20),user_id BIGINT,case_id BIGINT,oss_id BIGINT,created_at DATETIME DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(tenant_id,user_id,case_id,oss_id))");
        jdbc.execute("CREATE TEMPORARY TABLE sys_oss(oss_id BIGINT PRIMARY KEY,tenant_id VARCHAR(20),create_by BIGINT,file_name VARCHAR(200),original_name VARCHAR(200),file_suffix VARCHAR(20),create_time DATETIME)");
        login=mockStatic(LoginHelper.class);login.when(LoginHelper::getUserId).thenAnswer(c->user);login.when(LoginHelper::getTenantId).thenAnswer(c->tenant);
        var access=mock(CaseAccess.class);
        org.mockito.stubbing.Answer<LawyerCase> owned=c->{
            Long id=c.getArgument(0);
            var rows=jdbc.queryForList("SELECT * FROM lawyer_case WHERE id=? AND tenant_id=? AND user_id=? AND deleted=0",id,tenant,user);
            if(rows.isEmpty())throw new ServiceException("案件不可用",404);
            var value=new LawyerCase();value.setId(id);value.setUserId(user);value.setTenantId(tenant);return value;
        };
        when(access.owned(anyLong())).thenAnswer(owned);when(access.owned(anyLong(),anyBoolean())).thenAnswer(owned);
        tasks=new CaseTaskService(jdbc,access);oss=mock(OssService.class);
        when(oss.selectByIds(anyString())).thenAnswer(c->{var out=new ArrayList<OssDTO>();for(var id:((String)c.getArgument(0)).split(",")){var dto=new OssDTO();dto.setOssId(Long.valueOf(id));dto.setUrl("https://example.org/signed/"+id);out.add(dto);}return out;});
        materials=new LawyerCaseService(mock(LawyerCaseMapper.class),access,mock(ChatSessionMapper.class),jdbc,oss);
    }
    @AfterEach void close(){if(login!=null)login.close();if(source!=null)source.destroy();}
    <T>T call(Supplier<T> action){return tx.execute(s->action.get());}
    @Test void checklistPersistsAndUncheckClearsTimestampWithIdempotentDesiredState() {
        var task=call(()->tasks.create(11L,new Create("准备证据",LocalDateTime.of(2026,10,20,9,0))));
        var check=call(()->tasks.addChecklist(task.id(),new Checklist("核实合同原件")));
        var first=call(()->tasks.toggle(task.id(),check.id(),true));assertTrue(first.checked());assertNotNull(first.checkedAt());
        assertEquals(first.checkedAt(),call(()->tasks.toggle(task.id(),check.id(),true)).checkedAt());
        var reload=tasks.list(11L);assertEquals(1,reload.checkedItems());assertTrue(reload.tasks().get(0).checklist().get(0).checked());
        var unchecked=call(()->tasks.toggle(task.id(),check.id(),false));assertFalse(unchecked.checked());assertNull(unchecked.checkedAt());assertEquals(0,tasks.list(11L).checkedItems());
        var second=call(()->tasks.create(11L,new Create("另一任务",null)));
        assertEquals(404,assertThrows(ServiceException.class,()->call(()->tasks.toggle(second.id(),check.id(),true))).getCode());
    }
    @Test void statusDeadlineOrderingAndWorkMinutesAccumulateWithoutDuplicatingRetries() {
        var task=call(()->tasks.create(11L,new Create("庭前准备",null)));
        var deadline=LocalDateTime.of(2026,10,21,10,0);
        var updated=call(()->tasks.patch(task.id(),new Patch("准备开庭","doing",deadline,null,10)));
        assertEquals("doing",updated.status());assertEquals(deadline,updated.dueAt());
        var input=new Time(30,"证据整理","request-one");call(()->tasks.addTime(task.id(),input));call(()->tasks.addTime(task.id(),input));
        call(()->tasks.addTime(task.id(),new Time(15,"会议记录","request-two")));
        assertEquals(45,tasks.list(11L).workMinutes());assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM lawyer_case_task_time",Integer.class));
        assertEquals("证据整理",jdbc.queryForObject("SELECT note FROM lawyer_case_task_time WHERE request_id='request-one'",String.class));
        assertEquals(409,assertThrows(ServiceException.class,()->call(()->tasks.addTime(task.id(),new Time(99,"改写","request-one")))).getCode());
        assertThrows(ServiceException.class,()->call(()->tasks.addTime(task.id(),new Time(0,null,null))));
        call(()->tasks.patch(task.id(),new Patch(null,"done",null,true,null)));
        assertEquals(1,tasks.list(11L).doneTasks());assertNull(tasks.list(11L).tasks().get(0).dueAt());
        call(()->tasks.create(11L,new Create("后续任务",null)));assertEquals(2,tasks.list(11L).totalTasks());
    }
    @Test void foreignUserTenantAndDeletedCasesCannotReadOrMutateTasks() {
        var task=call(()->tasks.create(11L,new Create("个人任务",null)));var check=call(()->tasks.addChecklist(task.id(),new Checklist("私有检查项")));
        user=202L;
        for(boolean otherTenant:List.of(false,true)) {
            if(otherTenant){user=101L;tenant="111111";}
            assertEquals(404,assertThrows(ServiceException.class,()->tasks.list(11L)).getCode());
            assertThrows(ServiceException.class,()->call(()->tasks.create(11L,new Create("注入",null))));
            assertThrows(ServiceException.class,()->call(()->tasks.patch(task.id(),new Patch("篡改","done",null,null,null))));
            assertThrows(ServiceException.class,()->call(()->tasks.addChecklist(task.id(),new Checklist("注入"))));
            assertThrows(ServiceException.class,()->call(()->tasks.toggle(task.id(),check.id(),true)));
            assertThrows(ServiceException.class,()->call(()->tasks.addTime(task.id(),new Time(60,null,"foreign"))));
        }
        user=101L;tenant="000000";assertEquals(0,tasks.list(11L).workMinutes());assertEquals(1,tasks.list(11L).totalItems());
        jdbc.update("UPDATE lawyer_case SET deleted=1 WHERE id=11");
        assertThrows(ServiceException.class,()->tasks.list(11L));assertThrows(ServiceException.class,()->call(()->tasks.toggle(task.id(),check.id(),true)));
    }
    @Test void materialViewReusesOldBindingsAndContainsUploadDatesAndProtectedDownload() throws Exception {
        jdbc.execute("INSERT INTO sys_oss VALUES(71,'000000',101,'stored.pdf','合同.pdf','.pdf','2026-10-16 09:10:11'),(72,'000000',202,'foreign.pdf','他人材料.pdf','.pdf','2026-10-17 08:00:00')");
        jdbc.execute("INSERT INTO lawyer_case_file(tenant_id,user_id,case_id,oss_id) VALUES('000000',101,11,71),('000000',101,11,72)");
        var files=materials.files(11L);assertEquals(1,files.size());assertEquals("合同.pdf",files.get(0).originalName());
        assertEquals(LocalDateTime.of(2026,10,16,9,10,11),files.get(0).uploadedAt());assertTrue(files.get(0).downloadUrl().endsWith("/71/download"));
        assertEquals("/lawyer/case/11/files/71/download",materials.file(11L,71L).url());
        var response=new MockHttpServletResponse();materials.download(11L,71L,response);verify(oss).downloadFile(71L,response);
        assertThrows(ServiceException.class,()->materials.download(11L,72L,response));
        user=202L;assertThrows(ServiceException.class,()->materials.files(11L));assertThrows(ServiceException.class,()->materials.download(11L,71L,response));
        user=101L;tenant="111111";assertThrows(ServiceException.class,()->materials.files(11L));
    }
}
