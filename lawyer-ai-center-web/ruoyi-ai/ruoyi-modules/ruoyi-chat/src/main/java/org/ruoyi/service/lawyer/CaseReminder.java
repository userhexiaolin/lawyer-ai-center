package org.ruoyi.service.lawyer;
import org.ruoyi.domain.entity.lawyer.LawyerCase;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
public record CaseReminder(Long caseId,String title,String kind,LocalDate date,long days,String level) {
    public static List<CaseReminder> from(List<LawyerCase> cases,LocalDate today) {
        List<CaseReminder> result=new ArrayList<>();
        for(var value:cases){if(!"进行中".equals(value.getStatus()))continue;add(result,value,"开庭 / 关键日期",value.getNextHearingDate(),today);add(result,value,"诉讼时效到期",value.getLimitationDate(),today);}
        result.sort(Comparator.comparing(CaseReminder::date).thenComparing(CaseReminder::caseId).thenComparing(CaseReminder::kind));return result;
    }
    private static void add(List<CaseReminder> target,LawyerCase value,String kind,LocalDate date,LocalDate today){
        if(date==null)return;long days=ChronoUnit.DAYS.between(today,date);if(days<0||days>7)return;
        target.add(new CaseReminder(value.getId(),value.getTitle(),kind,date,days,days<=3?"danger":"warning"));
    }
}
