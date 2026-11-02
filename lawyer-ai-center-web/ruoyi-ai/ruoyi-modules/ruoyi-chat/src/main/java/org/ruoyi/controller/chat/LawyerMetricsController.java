package org.ruoyi.controller.chat;

import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;

/** Aggregates only the administrator's tenant, using Beijing calendar boundaries. */
@RestController @RequiredArgsConstructor @SaCheckLogin
@RequestMapping("/lawyer/metrics")
public class LawyerMetricsController {
    private final JdbcTemplate jdbc;
    public record Summary(String from,String until,String timezone,long newUsers,long activeUsers,
                          long consultations,long reviews,long paidOrders,BigDecimal paidAmount) {}
    @GetMapping public R<Summary> summary(@RequestParam(defaultValue="1") int days) {
        if (!LoginHelper.isSuperAdmin() && !LoginHelper.isTenantAdmin())
            throw new ServiceException("仅管理员可以查看使用统计",403);
        if (!Set.of(1,7,30).contains(days)) throw new ServiceException("统计范围仅支持今日、近7日或近30日");
        String tenant=LoginHelper.getTenantId();
        if (tenant==null) throw new ServiceException("租户信息缺失",403);
        ZoneId zone=ZoneId.of("Asia/Shanghai");
        LocalDate today=LocalDate.now(zone), first=today.minusDays(days-1);
        Timestamp from=Timestamp.valueOf(first.atStartOfDay()), until=Timestamp.valueOf(today.plusDays(1).atStartOfDay());
        long users=count("SELECT COUNT(*) FROM sys_user WHERE tenant_id=? AND del_flag='0' AND create_time>=? AND create_time<?",tenant,from,until);
        long active=count("SELECT COUNT(DISTINCT a.user_id) FROM (SELECT user_id FROM chat_message WHERE tenant_id=? AND role='user' AND create_time>=? AND create_time<? UNION ALL SELECT user_id FROM chat_session WHERE tenant_id=? AND create_time>=? AND create_time<?) a JOIN sys_user u ON u.user_id=a.user_id AND u.tenant_id=? AND u.del_flag='0'",tenant,from,until,tenant,from,until,tenant);
        long consultations=count("SELECT COUNT(*) FROM chat_session WHERE tenant_id=? AND usage_kind='CHAT' AND create_time>=? AND create_time<?",tenant,from,until);
        // Each user message in an immutable REVIEW session is a submission, including follow-ups.
        long reviews=count("SELECT COUNT(*) FROM chat_message m JOIN chat_session s ON s.id=m.session_id AND s.user_id=m.user_id AND s.tenant_id=m.tenant_id WHERE m.tenant_id=? AND s.usage_kind='REVIEW' AND m.role='user' AND m.create_time>=? AND m.create_time<?",tenant,from,until);
        var paid=jdbc.queryForMap("SELECT COUNT(*) n,COALESCE(SUM(amount),0) amount FROM lawyer_billing_order WHERE tenant_id=? AND status='PAID' AND paid_at>=? AND paid_at<?",tenant,from,until);
        return R.ok(new Summary(first.toString(),today.toString(),zone.getId(),users,active,consultations,reviews,
            ((Number)paid.get("n")).longValue(),new BigDecimal(paid.get("amount").toString())));
    }
    private long count(String sql,Object... args){Long result=jdbc.queryForObject(sql,Long.class,args);return result==null?0:result;}
}
