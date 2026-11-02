package org.ruoyi.service.lawyer;
import java.time.*;
public final class WorkTimeMath {
 private WorkTimeMath(){}
 public static long seconds(Instant start,Instant end){return Math.max(0,Duration.between(start,end).getSeconds());}
 public static long overlap(Instant start,Instant end,Instant from,Instant to){return seconds(start.isAfter(from)?start:from,end.isBefore(to)?end:to);}
}
