package org.ruoyi.system.service;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.service.PersonalNoticePublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

/** Recipient-scoped facade of sys_notice, shared with the existing announcement infrastructure. */
@Service @RequiredArgsConstructor
public class PersonalNoticeService implements PersonalNoticePublisher {
    private final JdbcTemplate jdbc;
    public record Notice(@JsonFormat(shape=JsonFormat.Shape.STRING) Long id,String title,String content,
        @JsonFormat(shape=JsonFormat.Shape.STRING) Long caseId,LocalDateTime createdAt,LocalDateTime readAt) {}
    public record Inbox(List<Notice> items,@JsonFormat(shape=JsonFormat.Shape.STRING) Long nextId,long unread) {}
    private LocalDateTime now(){return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).withNano(0);}
    @Override public boolean publish(Delivery d){
        if(d==null||d.tenantId()==null||d.tenantId().isBlank()||d.userId()==null||d.userId()<=0||d.key()==null||!d.key().matches("[A-Za-z0-9:_-]{1,191}")||d.title()==null||d.title().isBlank()||d.title().codePointCount(0,d.title().length())>50||d.content()==null||d.content().isBlank()||d.content().length()>20000)throw new IllegalArgumentException("Invalid personal notice");
        var time=now();
        try {
            jdbc.update("INSERT INTO sys_notice(notice_id,tenant_id,notice_title,notice_type,notice_content,status,create_by,create_time,recipient_user_id,reminder_key,related_case_id) VALUES(?,?,?,'1',?,'0',?,?,?,?,?)",
                IdUtil.getSnowflakeNextId(),d.tenantId(),d.title(),d.content(),d.userId(),time,d.userId(),d.key(),d.caseId());
            return true;
        }catch(DuplicateKeyException duplicate){
            Long count=jdbc.queryForObject("SELECT COUNT(*) FROM sys_notice WHERE tenant_id=? AND recipient_user_id=? AND reminder_key=?",Long.class,d.tenantId(),d.userId(),d.key());
            if(count!=null&&count>0)return false;throw duplicate;
        }
    }
    public Inbox list(String tenant,Long user,Long beforeId,int limit){
        if(limit<1||limit>50||(beforeId!=null&&beforeId<=0))throw new ServiceException("通知分页参数无效",400);
        String owned=" FROM sys_notice WHERE tenant_id=? AND recipient_user_id=? AND status='0'";
        var args=new ArrayList<Object>(List.of(tenant,user));if(beforeId!=null)args.add(beforeId);args.add(limit);
        var rows=jdbc.query("SELECT notice_id,notice_title,CONVERT(notice_content USING utf8mb4) AS content,related_case_id,create_time,read_time"+owned+(beforeId==null?"":" AND notice_id<?")+" ORDER BY notice_id DESC LIMIT ?",(r,n)->new Notice(r.getLong("notice_id"),r.getString("notice_title"),r.getString("content"),r.getObject("related_case_id",Long.class),r.getTimestamp("create_time").toLocalDateTime(),r.getTimestamp("read_time")==null?null:r.getTimestamp("read_time").toLocalDateTime()),args.toArray());
        Long unread=jdbc.queryForObject("SELECT COUNT(*)"+owned+" AND read_time IS NULL",Long.class,tenant,user);
        return new Inbox(rows,rows.size()==limit?rows.get(rows.size()-1).id():null,unread==null?0:unread);
    }
    public void markRead(String tenant,Long user,Long id){
        int changed=jdbc.update("UPDATE sys_notice SET read_time=COALESCE(read_time,?) WHERE notice_id=? AND tenant_id=? AND recipient_user_id=? AND status='0'",now(),id,tenant,user);
        if(changed==0){
            Long count=jdbc.queryForObject("SELECT COUNT(*) FROM sys_notice WHERE notice_id=? AND tenant_id=? AND recipient_user_id=? AND status='0'",Long.class,id,tenant,user);
            if(count==null||count==0)throw new ServiceException("通知不存在或无访问权限",404);
        }
    }
}
