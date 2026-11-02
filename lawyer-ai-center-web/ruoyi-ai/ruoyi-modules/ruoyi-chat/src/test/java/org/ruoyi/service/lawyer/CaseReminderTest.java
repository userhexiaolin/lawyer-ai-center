package org.ruoyi.service.lawyer;
import org.junit.jupiter.api.*;
import org.ruoyi.domain.entity.lawyer.LawyerCase;
import java.time.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("dev") class CaseReminderTest {
 @Test void inclusiveSevenDayBoundariesAndStatus(){var today=LocalDate.of(2026,10,16);var values=new ArrayList<LawyerCase>();for(int n:new int[]{-1,0,3,4,7,8}){var c=new LawyerCase();c.setId((long)n+10);c.setTitle("测试");c.setStatus("进行中");c.setNextHearingDate(today.plusDays(n));values.add(c);}var closed=new LawyerCase();closed.setStatus("已结案");closed.setNextHearingDate(today);values.add(closed);var result=CaseReminder.from(values,today);assertEquals(List.of(0L,3L,4L,7L),result.stream().map(CaseReminder::days).toList());assertEquals(List.of("danger","danger","warning","warning"),result.stream().map(CaseReminder::level).toList());}
 @Test void bothDatesAppearSeparately(){var c=new LawyerCase();c.setId(1L);c.setStatus("进行中");var today=LocalDate.of(2026,10,16);c.setNextHearingDate(today);c.setLimitationDate(today.plusDays(1));assertEquals(2,CaseReminder.from(List.of(c),today).size());}
}
