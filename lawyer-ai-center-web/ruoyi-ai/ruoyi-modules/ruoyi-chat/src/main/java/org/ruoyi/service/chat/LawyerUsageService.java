package org.ruoyi.service.chat;

import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class LawyerUsageService {
    private final JdbcTemplate jdbc;
    private org.ruoyi.service.billing.BillingProperties billingProperties = new org.ruoyi.service.billing.BillingProperties();
    @org.springframework.beans.factory.annotation.Autowired
    public void setBillingProperties(org.ruoyi.service.billing.BillingProperties properties){billingProperties=properties;}
    private org.ruoyi.service.billing.LawyerBillingService billing;
    @org.springframework.beans.factory.annotation.Autowired
    public void setBilling(org.ruoyi.service.billing.LawyerBillingService service){billing=service;}
    public static final String EXHAUSTED = "今日额度已用完，请购买额度、开通会员或明天再试";
    public record Usage(String date, int usedSessions, int sessionLimit, int remainingSessions) {}
    private LocalDate day() { return LocalDate.now(ZoneId.of("Asia/Shanghai")); }
    private String tenant() { return LoginHelper.getTenantId(); }
    public Usage read() {
        var date = day();
        // Local deployment has no quota account; do not manufacture unlimited credits.
        if (!billingProperties.isEnabled()) return new Usage(date.toString(), 0, 0, 0);
        var values = jdbc.query("SELECT used_sessions,session_limit,"+(billing==null?"used_sessions":"used_free_sessions")+" free_used FROM lawyer_daily_usage WHERE tenant_id=? AND user_id=? AND usage_date=?",
            (r, i) -> new Usage(date.toString(), r.getInt(1), r.getInt(2), Math.max(0, r.getInt(2) - r.getInt(3))), tenant(), LoginHelper.getUserId(), date);
        return values.isEmpty() ? new Usage(date.toString(), 0, 50, 50) : values.get(0);
    }
    /** First effective message, once per session as in the original product. No entry reservation. */
    @Transactional(rollbackFor = Exception.class)
    public void beginProcessing(String tenant, Long userId, Long sessionId) {
        if (tenant == null || userId == null || sessionId == null) throw new ServiceException("对话会话不可用");
        var rows = jdbc.queryForList("SELECT s.usage_started,s.usage_kind FROM chat_session s JOIN sys_user u ON u.user_id=s.user_id WHERE s.id=? AND s.user_id=? AND u.tenant_id=? AND u.del_flag='0' FOR UPDATE", sessionId, userId, tenant);
        if (rows.size() != 1) throw new ServiceException("对话会话不可用");
        // Keep ownership validation, skip all quota/ledger/usage-marker writes.
        if (!billingProperties.isEnabled()) return;
        var row = rows.get(0);
        if (((Number) row.get("usage_started")).intValue() != 0) return;
        consume(tenant, userId, "REVIEW".equals(row.get("usage_kind")));
        jdbc.update("UPDATE chat_session SET usage_started=1 WHERE id=? AND user_id=?", sessionId, userId);
    }

    /** Atomic UPDATE prevents overspend; called only after a valid message is accepted. */
    @Transactional(rollbackFor = Exception.class)
    public void consumeSession(Long userId) {
        consumeSession(userId,false);
    }
    @Transactional(rollbackFor = Exception.class)
    public void consumeSession(Long userId,boolean review) {
        if (userId == null || !userId.equals(LoginHelper.getUserId())) throw new ServiceException("登录用户不可用");
        consume(tenant(), userId, review);
    }
    private void consume(String tenant, Long userId, boolean review) {
        if (!billingProperties.isEnabled()) return;
        var date = day();
        boolean entitled=billing!=null&&billing.consumeEntitlement(tenant,userId,review?"REVIEW":"CHAT");
        // INSERT IGNORE takes shared duplicate-key locks; concurrent UPDATE upgrades can deadlock.
        // The no-op upsert acquires an exclusive lock before the conditional decrement.
        jdbc.update("INSERT INTO lawyer_daily_usage(tenant_id,user_id,usage_date) VALUES(?,?,?) ON DUPLICATE KEY UPDATE session_limit=lawyer_daily_usage.session_limit", tenant, userId, date);
        String sql=billing==null?"UPDATE lawyer_daily_usage SET used_sessions=used_sessions+1 WHERE tenant_id=? AND user_id=? AND usage_date=? AND used_sessions < session_limit"
            : entitled?"UPDATE lawyer_daily_usage SET used_sessions=used_sessions+1 WHERE tenant_id=? AND user_id=? AND usage_date=?"
            :"UPDATE lawyer_daily_usage SET used_sessions=used_sessions+1,used_free_sessions=used_free_sessions+1 WHERE tenant_id=? AND user_id=? AND usage_date=? AND used_free_sessions < session_limit";
        int changed = jdbc.update(sql, tenant, userId, date);
        if (changed != 1) throw new ServiceException(EXHAUSTED, 429);
    }
}
