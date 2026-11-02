package org.ruoyi.system.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.ruoyi.common.core.service.PersonalNoticePublisher.Delivery;
import org.ruoyi.common.core.exception.ServiceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** Connection-local temporary table; does not insert into the real notification table. */
@Tag("dev") @EnabledIfSystemProperty(named="personal.notice.mysql",matches="true")
class PersonalNoticeMysqlTest {
    SingleConnectionDataSource source;JdbcTemplate jdbc;PersonalNoticeService service;
    static String setting(String text,String key){var m=Pattern.compile("(?m)^          "+key+": (.+)$").matcher(text);if(!m.find())throw new IllegalStateException("Missing local datasource setting");return m.group(1).strip();}
    @BeforeEach void setup() throws Exception {
        var config=Files.readString(Path.of(System.getProperty("personal.notice.workspace")).resolve("ruoyi-ai/ruoyi-admin/src/main/resources/application-dev.yml"));
        source=new SingleConnectionDataSource(DriverManager.getConnection(setting(config,"url"),setting(config,"username"),setting(config,"password")),true);jdbc=new JdbcTemplate(source);service=new PersonalNoticeService(jdbc);
        jdbc.execute("CREATE TEMPORARY TABLE sys_notice(notice_id BIGINT PRIMARY KEY,tenant_id VARCHAR(20),notice_title VARCHAR(50),notice_type CHAR(1),notice_content LONGBLOB,status CHAR(1),create_by BIGINT,create_time DATETIME,recipient_user_id BIGINT,reminder_key VARCHAR(191),related_case_id BIGINT,read_time DATETIME,UNIQUE KEY uk_notice_recipient_key(tenant_id,recipient_user_id,reminder_key))");
    }
    @AfterEach void close(){if(source!=null)source.destroy();}
    Delivery delivery(String tenant,long user,String key){return new Delivery(tenant,user,key,"明日开庭提醒","案件：合成测试\n开庭：明日",11L);}
    @Test void persistenceUniqueRecipientKeyAndReadStateSurviveServiceReload(){
        var d=delivery("000000",101,"case-date:11:2026-10-19");assertTrue(service.publish(d));assertFalse(service.publish(d));
        var first=service.list("000000",101L,null,20);assertEquals(1,first.items().size());assertEquals(1,first.unread());assertEquals(d.content(),first.items().get(0).content());
        long id=first.items().get(0).id();service.markRead("000000",101L,id);var read=service.list("000000",101L,null,20).items().get(0).readAt();assertNotNull(read);
        service=new PersonalNoticeService(jdbc);assertFalse(service.publish(d));service.markRead("000000",101L,id);
        var reloaded=service.list("000000",101L,null,20);assertEquals(0,reloaded.unread());assertEquals(read,reloaded.items().get(0).readAt());
        assertTrue(service.publish(delivery("000000",101,"case-date:11:2026-10-20")));assertEquals(2,service.list("000000",101L,null,20).items().size());
    }
    @Test void otherRecipientOtherTenantAndAnnouncementsCannotLeakIntoInboxOrBeMarkedRead(){
        String key="case-date:11:2026-10-19";service.publish(delivery("000000",101,key));service.publish(delivery("000000",202,key));service.publish(delivery("111111",101,key));
        jdbc.update("INSERT INTO sys_notice(notice_id,tenant_id,notice_title,notice_type,status,create_time) VALUES(1,'000000','公告','2','0',NOW())");
        var owner=service.list("000000",101L,null,20);assertEquals(1,owner.items().size());long ownId=owner.items().get(0).id();
        assertNotEquals(ownId,service.list("000000",202L,null,20).items().get(0).id());
        assertNotEquals(ownId,service.list("111111",101L,null,20).items().get(0).id());
        assertTrue(service.list("000000",303L,null,20).items().isEmpty());
        assertEquals(404,assertThrows(ServiceException.class,()->service.markRead("000000",202L,ownId)).getCode());
        assertEquals(404,assertThrows(ServiceException.class,()->service.markRead("111111",101L,ownId)).getCode());
        assertEquals(404,assertThrows(ServiceException.class,()->service.markRead("000000",101L,1L)).getCode());
        assertEquals(1,service.list("000000",101L,null,20).unread());
    }
    @Test void boundedCursorPaginationAndValidation(){
        for(int n=0;n<3;n++)service.publish(delivery("000000",101,"case-task:"+n+":20261019100000"));
        var first=service.list("000000",101L,null,2);assertEquals(2,first.items().size());assertNotNull(first.nextId());
        var last=service.list("000000",101L,first.nextId(),2);assertEquals(1,last.items().size());assertNull(last.nextId());assertEquals(3,last.unread());
        assertThrows(ServiceException.class,()->service.list("000000",101L,null,1000));assertThrows(IllegalArgumentException.class,()->service.publish(delivery("000000",101,"invalid key")));
    }
}
