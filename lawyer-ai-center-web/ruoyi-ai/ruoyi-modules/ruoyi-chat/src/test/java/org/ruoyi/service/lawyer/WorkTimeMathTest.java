package org.ruoyi.service.lawyer;
import org.junit.jupiter.api.*;import java.time.*;import static org.junit.jupiter.api.Assertions.*;
@Tag("dev")class WorkTimeMathTest{
 @Test void dayAndWeekClipAtShanghaiMidnight(){var from=Instant.parse("2026-10-15T16:00:00Z");var to=from.plusSeconds(3600);assertEquals(3600,WorkTimeMath.overlap(from.minusSeconds(1800),to,from,to));assertEquals(0,WorkTimeMath.overlap(from.minusSeconds(3600),from.minusSeconds(1),from,to));}
 @Test void backwardsClockNeverCreatesNegativeDuration(){var now=Instant.now();assertEquals(0,WorkTimeMath.seconds(now,now.minusSeconds(1)));assertEquals(90,WorkTimeMath.seconds(now,now.plusSeconds(90)));}
}
