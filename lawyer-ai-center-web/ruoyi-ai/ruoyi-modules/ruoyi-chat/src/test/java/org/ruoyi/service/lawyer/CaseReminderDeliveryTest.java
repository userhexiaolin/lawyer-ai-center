package org.ruoyi.service.lawyer;

import org.junit.jupiter.api.*;
import org.ruoyi.common.core.service.PersonalNoticePublisher;
import org.springframework.jdbc.core.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("dev")
class CaseReminderDeliveryTest {
    LocalDateTime now=LocalDateTime.of(2026,10,18,10,0);List<PersonalNoticePublisher.Delivery> sent=new ArrayList<>();Set<String> keys=new HashSet<>();
    PersonalNoticePublisher publisher=d->{sent.add(d);return keys.add(d.tenantId()+":"+d.userId()+":"+d.key());};
    List<CaseReminderDelivery.Dates> dates=new ArrayList<>();List<CaseReminderDelivery.Task> tasks=new ArrayList<>();List<String> sql=new ArrayList<>();
    JdbcTemplate jdbc=new JdbcTemplate(){@Override public <T>List<T> query(String statement,RowMapper<T> row,Object... args){sql.add(statement);return (List<T>)(statement.startsWith("SELECT c.id")?dates:tasks);}};
    @Test void sameCaseSameDayCombinesKindsAndRepeatedScansDoNotCreateMore(){
        dates.add(new CaseReminderDelivery.Dates(11,"000000",101,"明日开庭",now.toLocalDate().plusDays(1),now.toLocalDate().plusDays(1)));
        var service=new CaseReminderDelivery(jdbc,publisher);assertEquals(new CaseReminderDelivery.Result(1,1),service.scan(now));assertEquals(0,service.scan(now.plusMinutes(5)).created());
        var notice=sent.get(0);assertEquals(101L,notice.userId());assertEquals("000000",notice.tenantId());assertEquals("case-date:11:2026-10-19",notice.key());assertTrue(notice.content().contains("开庭 / 关键日期、诉讼时效到期"));
        assertTrue(sql.stream().allMatch(s->s.contains("c.deleted=0")&&s.contains("c.status='进行中'")&&s.contains("u.tenant_id=c.tenant_id")));
    }
    @Test void sevenDayBoundsAndChangedDatesCreateOnlyEligibleKeys(){
        dates.add(new CaseReminderDelivery.Dates(11,"000000",101,"边界",now.toLocalDate().plusDays(7),now.toLocalDate().plusDays(8)));
        dates.add(new CaseReminderDelivery.Dates(12,"000000",101,"已过期",now.toLocalDate().minusDays(1),null));
        var service=new CaseReminderDelivery(jdbc,publisher);assertEquals(1,service.scan(now).created());assertEquals("case-date:11:2026-10-25",sent.get(0).key());
        dates.set(0,new CaseReminderDelivery.Dates(11,"000000",101,"改期",now.toLocalDate().plusDays(2),null));assertEquals(1,service.scan(now).created());
    }
    @Test void taskDeliveryRetainsOwnerAndDeadlineWithNoCrossCaseOwnerJoin(){
        tasks.add(new CaseReminderDelivery.Task(21,"111111",202,12,"案件二","交证据",now.plusDays(1)));
        var service=new CaseReminderDelivery(jdbc,publisher);assertEquals(1,service.scan(now).created());assertEquals(0,service.scan(now).created());
        var notice=sent.get(0);assertEquals(202L,notice.userId());assertEquals("111111",notice.tenantId());assertEquals("case-task:21:20261019100000",notice.key());assertEquals(12L,notice.caseId());
        assertTrue(sql.get(1).contains("c.tenant_id=t.tenant_id AND c.user_id=t.user_id"));assertTrue(sql.get(1).contains("t.status IN ('todo','doing')"));
    }
}
