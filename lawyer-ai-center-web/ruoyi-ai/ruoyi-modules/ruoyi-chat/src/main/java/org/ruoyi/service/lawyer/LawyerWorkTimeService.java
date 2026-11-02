package org.ruoyi.service.lawyer;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.entity.chat.ChatSession;
import org.ruoyi.mapper.chat.ChatSessionMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;import java.time.temporal.TemporalAdjusters;import java.sql.*;import java.util.*;
@Service @RequiredArgsConstructor
public class LawyerWorkTimeService {
 private final JdbcTemplate jdbc;private final CaseAccess cases;private final ChatSessionMapper sessions;
 public record Entry(Long id,Long caseId,Long sessionId,Instant startTime,Instant endTime,long durationSeconds,String description,String caseTitle,String sessionTitle){}
 public record Summary(Entry active,List<Entry> rows,long total,int page,long daySeconds,long weekSeconds,long totalSeconds,Instant serverNow){}
 private Entry row(ResultSet r,int n)throws SQLException{return new Entry(r.getLong("id"),r.getObject("case_id",Long.class),r.getObject("session_id",Long.class),r.getTimestamp("start_time").toInstant(),r.getTimestamp("end_time")==null?null:r.getTimestamp("end_time").toInstant(),r.getLong("duration_seconds"),r.getString("description"),null,null);}
 private Entry active(){return active(false);}
 private Entry active(boolean lock){var list=jdbc.query("SELECT * FROM lawyer_work_time WHERE tenant_id=? AND user_id=? AND active_slot=1"+(lock?" FOR UPDATE":""),this::row,LoginHelper.getTenantId(),LoginHelper.getUserId());return list.isEmpty()?null:list.get(0);}
 @Transactional(rollbackFor=Exception.class) public Entry start(Long caseId,Long sessionId,String description){
  if(sessionId!=null){var session=sessions.selectOne(Wrappers.<ChatSession>lambdaQuery().eq(ChatSession::getId,sessionId).eq(ChatSession::getUserId,LoginHelper.getUserId()));if(session==null)throw new ServiceException("会话不可用",404);if(caseId!=null&&session.getCaseId()!=null&&!caseId.equals(session.getCaseId()))throw new ServiceException("会话已属于其他案件，请核对后重试",400);if(caseId==null)caseId=session.getCaseId();}
  if(caseId!=null)cases.owned(caseId,true);
  // Lock an existing owner row so first-start requests serialize before an active row exists.
  jdbc.queryForObject("SELECT user_id FROM sys_user WHERE tenant_id=? AND user_id=? FOR UPDATE",Long.class,LoginHelper.getTenantId(),LoginHelper.getUserId());
  var existing=active(true);if(existing!=null)return existing;
  var now=Timestamp.from(Instant.now());
  jdbc.update("INSERT INTO lawyer_work_time(id,tenant_id,user_id,case_id,session_id,start_time,description,created_at,active_slot) VALUES(?,?,?,?,?,?,?,?,1)",IdUtil.getSnowflakeNextId(),LoginHelper.getTenantId(),LoginHelper.getUserId(),caseId,sessionId,now,description,now);
  var value=active(true);if(value==null)throw new ServiceException("计时状态已变化，请重试",409);return value;
 }
 @Transactional(rollbackFor=Exception.class) public Entry stop(Long id){
  var values=jdbc.query("SELECT * FROM lawyer_work_time WHERE id=? AND tenant_id=? AND user_id=? FOR UPDATE",this::row,id,LoginHelper.getTenantId(),LoginHelper.getUserId());
  if(values.isEmpty())throw new ServiceException("计时记录不可用",404);var value=values.get(0);if(value.endTime()!=null)return value;
  var end=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);long seconds=WorkTimeMath.seconds(value.startTime(),end);
  jdbc.update("UPDATE lawyer_work_time SET end_time=?,duration_seconds=?,duration_minutes=?,active_slot=NULL WHERE id=? AND tenant_id=? AND user_id=?",Timestamp.from(end),seconds,java.math.BigDecimal.valueOf(seconds).divide(java.math.BigDecimal.valueOf(60),3,java.math.RoundingMode.HALF_UP),id,LoginHelper.getTenantId(),LoginHelper.getUserId());
  return new Entry(value.id(),value.caseId(),value.sessionId(),value.startTime(),end,seconds,value.description(),null,null);
 }
 public Summary summary(Long caseId,int page){
  if(caseId!=null)cases.owned(caseId);int p=Math.max(1,page);var now=Instant.now();var zone=ZoneId.of("Asia/Shanghai");var day=now.atZone(zone).toLocalDate();var dayStart=day.atStartOfDay(zone).toInstant();var weekStart=day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant();
  String where=" WHERE tenant_id=? AND user_id=?"+(caseId==null?"":" AND case_id=?");List<Object> args=new ArrayList<>(List.of(LoginHelper.getTenantId(),LoginHelper.getUserId()));if(caseId!=null)args.add(caseId);
  var count=jdbc.queryForObject("SELECT COUNT(*) FROM lawyer_work_time"+where,Long.class,args.toArray());
  var totalArgs=new ArrayList<Object>();totalArgs.add(Timestamp.from(now));totalArgs.addAll(args);
  Long total=jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN end_time IS NULL THEN GREATEST(0,TIMESTAMPDIFF(SECOND,start_time,?)) ELSE duration_seconds END),0) FROM lawyer_work_time"+where,Long.class,totalArgs.toArray());
  var recentArgs=new ArrayList<>(args);recentArgs.add(Timestamp.from(weekStart));
  var recent=jdbc.query("SELECT * FROM lawyer_work_time"+where+" AND (end_time>=? OR end_time IS NULL)",this::row,recentArgs.toArray());long daySeconds=0,weekSeconds=0;for(var r:recent){var end=r.endTime()==null?now:r.endTime();daySeconds+=WorkTimeMath.overlap(r.startTime(),end,dayStart,now);weekSeconds+=WorkTimeMath.overlap(r.startTime(),end,weekStart,now);}
  var historyArgs=new ArrayList<>(args);historyArgs.add((long)(p-1)*20);
  var rows=jdbc.query("SELECT * FROM lawyer_work_time"+where+" ORDER BY start_time DESC,id DESC LIMIT 20 OFFSET ?",this::row,historyArgs.toArray());
  return new Summary(active(),rows,count==null?0:count,p,daySeconds,weekSeconds,total==null?0:total,now);
 }
}
