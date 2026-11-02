package org.ruoyi.service.lawyer;

import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.service.PersonalNoticePublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** All-tenant background scan: recipient comes only from live, owner-matched database rows. */
@Service @RequiredArgsConstructor
public class CaseReminderDelivery {
    private final JdbcTemplate jdbc;
    private final PersonalNoticePublisher notices;
    record Dates(long id,String tenant,long user,String title,LocalDate hearing,LocalDate limitation) {}
    record Task(long id,String tenant,long user,long caseId,String caseTitle,String title,LocalDateTime due) {}
    public record Result(int candidates,int created) {}
    private static final String ACTIVE=" JOIN sys_user u ON u.user_id=c.user_id AND u.tenant_id=c.tenant_id AND u.status='0' AND u.del_flag='0' WHERE c.deleted=0 AND c.status='进行中'";
    private static String heading(String text){return text.substring(0,text.offsetByCodePoints(0,Math.min(50,text.codePointCount(0,text.length()))));}
    public Result scan(LocalDateTime now){
        LocalDate today=now.toLocalDate(),last=today.plusDays(7);int candidates=0,created=0;long cursor=0;
        while(true){
            var batch=jdbc.query("SELECT c.id,c.tenant_id,c.user_id,c.title,c.next_hearing_date,c.limitation_date FROM lawyer_case c"+ACTIVE+" AND c.id>? AND (c.next_hearing_date BETWEEN ? AND ? OR c.limitation_date BETWEEN ? AND ?) ORDER BY c.id LIMIT 500",
                (r,n)->new Dates(r.getLong("id"),r.getString("tenant_id"),r.getLong("user_id"),r.getString("title"),r.getDate("next_hearing_date")==null?null:r.getDate("next_hearing_date").toLocalDate(),r.getDate("limitation_date")==null?null:r.getDate("limitation_date").toLocalDate()),cursor,today,last,today,last);
            if(batch.isEmpty())break;
            for(var value:batch){
                var dates=new TreeMap<LocalDate,List<String>>();
                if(value.hearing()!=null&&!value.hearing().isBefore(today)&&!value.hearing().isAfter(last))dates.computeIfAbsent(value.hearing(),k->new ArrayList<>()).add("开庭 / 关键日期");
                if(value.limitation()!=null&&!value.limitation().isBefore(today)&&!value.limitation().isAfter(last))dates.computeIfAbsent(value.limitation(),k->new ArrayList<>()).add("诉讼时效到期");
                for(var date:dates.entrySet()){
                    candidates++;String kinds=String.join("、",date.getValue());
                    if(notices.publish(new PersonalNoticePublisher.Delivery(value.tenant(),value.user(),"case-date:"+value.id()+":"+date.getKey(),heading(kinds+"提醒 · "+value.title()),"案件："+value.title()+"\n"+kinds+"："+date.getKey()+"\n请提前核对时间并准备材料。",value.id())))created++;
                }
            }
            cursor=batch.get(batch.size()-1).id();if(batch.size()<500)break;
        }
        cursor=0;
        while(true){
            var batch=jdbc.query("SELECT t.id,t.tenant_id,t.user_id,t.case_id,t.title,c.title AS case_title,t.due_at FROM lawyer_case_task t JOIN lawyer_case c ON c.id=t.case_id AND c.tenant_id=t.tenant_id AND c.user_id=t.user_id"+ACTIVE+" AND t.status IN ('todo','doing') AND t.id>? AND t.due_at>=? AND t.due_at<? ORDER BY t.id LIMIT 500",
                (r,n)->new Task(r.getLong("id"),r.getString("tenant_id"),r.getLong("user_id"),r.getLong("case_id"),r.getString("case_title"),r.getString("title"),r.getTimestamp("due_at").toLocalDateTime()),cursor,today.atStartOfDay(),last.plusDays(1).atStartOfDay());
            if(batch.isEmpty())break;
            for(var value:batch){
                candidates++;
                if(notices.publish(new PersonalNoticePublisher.Delivery(value.tenant(),value.user(),"case-task:"+value.id()+":"+value.due().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")),heading("任务到期提醒 · "+value.title()),"案件："+value.caseTitle()+"\n任务："+value.title()+"\n截止："+value.due().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))+"\n请及时处理或调整截止时间。",value.caseId())))created++;
            }
            cursor=batch.get(batch.size()-1).id();if(batch.size()<500)break;
        }
        return new Result(candidates,created);
    }
}
