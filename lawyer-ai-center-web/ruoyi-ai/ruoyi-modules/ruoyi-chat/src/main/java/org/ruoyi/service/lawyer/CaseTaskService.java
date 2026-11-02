package org.ruoyi.service.lawyer;

import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.bo.lawyer.CaseTaskInput;
import org.ruoyi.domain.vo.lawyer.CaseTaskView.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.*;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CaseTaskService {
    private final JdbcTemplate jdbc;
    private final CaseAccess cases;
    private static final String OWNED=" WHERE id=? AND tenant_id=? AND user_id=?";
    private LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).withNano(0); }
    private static LocalDateTime time(ResultSet r,String key) throws SQLException { var t=r.getTimestamp(key);return t==null?null:t.toLocalDateTime(); }
    private Task taskRow(ResultSet r,int n) throws SQLException {
        return new Task(r.getLong("id"),r.getLong("case_id"),r.getLong("user_id"),r.getString("title"),r.getString("status"),time(r,"due_at"),r.getInt("sort_order"),r.getLong("work_minutes"),time(r,"created_at"),time(r,"updated_at"),List.of());
    }
    private Checklist itemRow(ResultSet r,int n) throws SQLException {
        return new Checklist(r.getLong("id"),r.getLong("task_id"),r.getString("content"),r.getBoolean("checked"),time(r,"checked_at"),time(r,"created_at"));
    }
    private String title(String value) { if(value==null||value.isBlank()||value.length()>200)throw new ServiceException("任务标题必填且不能超过200字",400);return value.strip(); }
    private Task find(Long id,boolean lock) {
        var rows=jdbc.query("SELECT * FROM lawyer_case_task"+OWNED+(lock?" FOR UPDATE":""),this::taskRow,id,LoginHelper.getTenantId(),LoginHelper.getUserId());
        if(rows.isEmpty())throw new ServiceException("任务不存在或无访问权限",404);return rows.get(0);
    }
    /** Always lock case before task, sharing the existing case deletion/association lock order. */
    private Task owned(Long id) {
        var task=find(id,false);cases.owned(task.caseId(),true);return find(id,true);
    }
    private Task withItems(Task t) {
        var items=jdbc.query("SELECT * FROM lawyer_case_checklist_item WHERE task_id=? ORDER BY created_at,id",this::itemRow,t.id());
        return new Task(t.id(),t.caseId(),t.userId(),t.title(),t.status(),t.dueAt(),t.sortOrder(),t.workMinutes(),t.createdAt(),t.updatedAt(),items);
    }
    @Transactional(readOnly=true) public Summary list(Long caseId) {
        cases.owned(caseId);
        var rows=jdbc.query("SELECT * FROM lawyer_case_task WHERE case_id=? AND tenant_id=? AND user_id=? ORDER BY sort_order,id",this::taskRow,caseId,LoginHelper.getTenantId(),LoginHelper.getUserId());
        // At most 200 tasks / 100 checks per task. Load checks once instead of per-task queries.
        var checks=jdbc.query("SELECT i.* FROM lawyer_case_checklist_item i JOIN lawyer_case_task t ON t.id=i.task_id WHERE t.case_id=? AND t.tenant_id=? AND t.user_id=? ORDER BY i.created_at,i.id",this::itemRow,caseId,LoginHelper.getTenantId(),LoginHelper.getUserId());
        Map<Long,List<Checklist>> grouped=new HashMap<>();for(var item:checks)grouped.computeIfAbsent(item.taskId(),k->new ArrayList<>()).add(item);
        var tasks=rows.stream().map(t->new Task(t.id(),t.caseId(),t.userId(),t.title(),t.status(),t.dueAt(),t.sortOrder(),t.workMinutes(),t.createdAt(),t.updatedAt(),grouped.getOrDefault(t.id(),List.of()))).toList();
        return new Summary(tasks,(int)tasks.stream().filter(t->"done".equals(t.status())).count(),tasks.size(),(int)checks.stream().filter(Checklist::checked).count(),checks.size(),tasks.stream().mapToLong(Task::workMinutes).sum());
    }
    @Transactional(rollbackFor=Exception.class) public Task create(Long caseId,CaseTaskInput.Create input) {
        var owner=cases.owned(caseId,true);
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM lawyer_case_task WHERE case_id=? AND tenant_id=? AND user_id=?",Integer.class,caseId,owner.getTenantId(),owner.getUserId());
        if(count!=null&&count>=200)throw new ServiceException("每个案件最多200个任务",400);
        Integer order=jdbc.queryForObject("SELECT COALESCE(MAX(sort_order),-1)+1 FROM lawyer_case_task WHERE case_id=? AND tenant_id=? AND user_id=?",Integer.class,caseId,owner.getTenantId(),owner.getUserId());
        long id=IdUtil.getSnowflakeNextId();var now=now();
        jdbc.update("INSERT INTO lawyer_case_task(id,tenant_id,user_id,case_id,title,status,due_at,sort_order,work_minutes,created_at,updated_at) VALUES(?,?,?,?,?,'todo',?,?,0,?,?)",id,owner.getTenantId(),owner.getUserId(),caseId,title(input.title()),input.dueAt(),order,now,now);
        return withItems(find(id,false));
    }
    @Transactional(rollbackFor=Exception.class) public Task patch(Long id,CaseTaskInput.Patch input) {
        var t=owned(id);String title=input.title()==null?t.title():title(input.title());String status=input.status()==null?t.status():input.status();
        if(!List.of("todo","doing","done").contains(status))throw new ServiceException("任务状态无效",400);
        if(Boolean.TRUE.equals(input.clearDueAt())&&input.dueAt()!=null)throw new ServiceException("截止时间与清空截止时间不能同时设置",400);
        var due=Boolean.TRUE.equals(input.clearDueAt())?null:input.dueAt()==null?t.dueAt():input.dueAt();int order=input.sortOrder()==null?t.sortOrder():input.sortOrder();
        if(order<0||order>1000000)throw new ServiceException("任务排序值无效",400);
        jdbc.update("UPDATE lawyer_case_task SET title=?,status=?,due_at=?,sort_order=?,updated_at=?"+OWNED,title,status,due,order,now(),id,LoginHelper.getTenantId(),LoginHelper.getUserId());
        return withItems(find(id,false));
    }
    @Transactional(rollbackFor=Exception.class) public Checklist addChecklist(Long taskId,CaseTaskInput.Checklist input) {
        owned(taskId);String content=input.content();if(content==null||content.isBlank()||content.length()>1000)throw new ServiceException("检查项必填且不能超过1000字",400);
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM lawyer_case_checklist_item WHERE task_id=?",Integer.class,taskId);
        if(count!=null&&count>=100)throw new ServiceException("每个任务最多100个检查项",400);
        long id=IdUtil.getSnowflakeNextId();var now=now();
        jdbc.update("INSERT INTO lawyer_case_checklist_item(id,task_id,content,checked,created_at) VALUES(?,?,?,0,?)",id,taskId,content.strip(),now);
        touch(taskId,now);return new Checklist(id,taskId,content.strip(),false,null,now);
    }
    @Transactional(rollbackFor=Exception.class) public Checklist toggle(Long taskId,Long itemId,Boolean desired) {
        owned(taskId);var rows=jdbc.query("SELECT * FROM lawyer_case_checklist_item WHERE id=? AND task_id=? FOR UPDATE",this::itemRow,itemId,taskId);
        if(rows.isEmpty())throw new ServiceException("检查项不存在或不属于该任务",404);
        var item=rows.get(0);boolean checked=desired==null?!item.checked():desired;var now=now();
        if(checked!=item.checked()) {
            jdbc.update("UPDATE lawyer_case_checklist_item SET checked=?,checked_at=? WHERE id=? AND task_id=?",checked,checked?now:null,itemId,taskId);touch(taskId,now);
            return new Checklist(item.id(),taskId,item.content(),checked,checked?now:null,item.createdAt());
        }
        return item;
    }
    @Transactional(rollbackFor=Exception.class) public Task addTime(Long id,CaseTaskInput.Time input) {
        var task=owned(id);if(input.minutes()==null||input.minutes()<1||input.minutes()>1440)throw new ServiceException("工时须为1至1440的整数分钟",400);
        if(input.note()!=null&&input.note().length()>1000)throw new ServiceException("工时备注不能超过1000字",400);
        String key=input.requestId()==null?UUID.randomUUID().toString():input.requestId();
        if(!key.matches("[A-Za-z0-9_-]{1,64}"))throw new ServiceException("工时请求标识无效",400);
        var prior=jdbc.queryForList("SELECT minutes,note FROM lawyer_case_task_time WHERE task_id=? AND request_id=?",id,key);
        if(!prior.isEmpty()) {
            var row=prior.get(0);
            if(((Number)row.get("minutes")).intValue()!=input.minutes()||!Objects.equals(row.get("note"),input.note()))throw new ServiceException("此工时请求已提交，请刷新后重新登记",409);
            return withItems(task);
        }
        var now=now();
        jdbc.update("INSERT INTO lawyer_case_task_time(id,task_id,minutes,note,request_id,created_at) VALUES(?,?,?,?,?,?)",IdUtil.getSnowflakeNextId(),id,input.minutes(),input.note(),key,now);
        jdbc.update("UPDATE lawyer_case_task SET work_minutes=work_minutes+?,updated_at=?"+OWNED,input.minutes(),now,id,LoginHelper.getTenantId(),LoginHelper.getUserId());
        return withItems(find(id,false));
    }
    private void touch(Long id,LocalDateTime now) { jdbc.update("UPDATE lawyer_case_task SET updated_at=?"+OWNED,now,id,LoginHelper.getTenantId(),LoginHelper.getUserId()); }
}
