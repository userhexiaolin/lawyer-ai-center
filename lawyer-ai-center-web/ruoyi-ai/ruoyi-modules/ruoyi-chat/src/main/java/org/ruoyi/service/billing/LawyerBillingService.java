package org.ruoyi.service.billing;

import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service @RequiredArgsConstructor
public class LawyerBillingService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final BillingProperties properties;
    private final AlipayGateway gateway;
    private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter PAYMENT_TIME=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public record Plan(String code,String name,BigDecimal price,int chatCredits,int reviewCredits,int validDays,int memberDays) {}
    public record Catalog(boolean purchasingEnabled,List<Plan> plans) {}
    public record Account(long remainingChat,long remainingReview,LocalDateTime memberUntil,boolean memberActive) {}
    public record OrderView(String orderNo,String planName,BigDecimal amount,String status,String channel,
                            String payUrl,LocalDateTime createdAt,LocalDateTime expiresAt,LocalDateTime paidAt) {}
    private record Order(String no,String tenant,long user,Plan plan,String status,String appId,String tradeNo,
                         String payUrl,LocalDateTime createdAt,LocalDateTime expiresAt,LocalDateTime paidAt) {}
    private record MemberGrant(LocalDateTime grantedAt,int days) {}
    private static LocalDateTime now(){return LocalDateTime.now(ZONE);}
    private static String id(){return UUID.randomUUID().toString().replace("-","");}
    private static String tenant(){return LoginHelper.getTenantId();}
    private static long owner(){return LoginHelper.getUserId();}
    private static LocalDateTime date(ResultSet r,String column)throws SQLException {
        var value=r.getTimestamp(column);return value==null?null:value.toLocalDateTime();
    }
    public Catalog plans(){
        return new Catalog(properties.isEnabled(),jdbc.query("SELECT * FROM lawyer_billing_plan WHERE enabled=1 ORDER BY sort_order,code",
            (r,n)->new Plan(r.getString("code"),r.getString("name"),r.getBigDecimal("price"),r.getInt("chat_credits"),r.getInt("review_credits"),r.getInt("valid_days"),r.getInt("member_days"))));
    }
    private Order row(ResultSet r,int n)throws SQLException {
        return new Order(r.getString("order_no"),r.getString("tenant_id"),r.getLong("user_id"),
            new Plan(r.getString("plan_code"),r.getString("plan_name"),r.getBigDecimal("amount"),r.getInt("chat_credits"),r.getInt("review_credits"),r.getInt("valid_days"),r.getInt("member_days")),
            r.getString("status"),r.getString("app_id"),r.getString("trade_no"),r.getString("pay_url"),date(r,"created_at"),date(r,"expires_at"),date(r,"paid_at"));
    }
    private OrderView view(Order o){return new OrderView(o.no,o.plan.name,o.plan.price,o.status,"ALIPAY",o.status.equals("PENDING")?o.payUrl:null,o.createdAt,o.expiresAt,o.paidAt);}
    private Order owned(String no){
        if(no==null||!no.matches("[a-f0-9]{32}"))throw new ServiceException("订单不存在",404);
        var rows=jdbc.query("SELECT * FROM lawyer_billing_order WHERE order_no=? AND tenant_id=? AND user_id=?",this::row,no,tenant(),owner());
        if(rows.size()!=1)throw new ServiceException("订单不存在",404);return rows.get(0);
    }
    public List<OrderView> orders(int page){
        int offset=(Math.max(1,Math.min(page,10000))-1)*20;
        return jdbc.query("SELECT * FROM lawyer_billing_order WHERE tenant_id=? AND user_id=? ORDER BY created_at DESC,order_no DESC LIMIT 20 OFFSET ?",this::row,tenant(),owner(),offset).stream().map(this::view).toList();
    }
    public OrderView create(String planCode,String key){
        gateway.requireEnabled();
        if(key==null||!key.matches("[a-zA-Z0-9_-]{20,64}"))throw new ServiceException("订单请求标识无效，请刷新后重试");
        String t=tenant();long user=owner();
        Order order=transactions.execute(status->{
            lockAccount(t,user);
            var existing=jdbc.query("SELECT * FROM lawyer_billing_order WHERE tenant_id=? AND user_id=? AND idempotency_key=?",this::row,t,user,key);
            if(!existing.isEmpty()){
                if(!existing.get(0).plan.code.equals(planCode))throw new ServiceException("订单请求与原套餐不一致");
                return existing.get(0);
            }
            var choices=jdbc.query("SELECT * FROM lawyer_billing_plan WHERE code=? AND enabled=1",
                (r,n)->new Plan(r.getString("code"),r.getString("name"),r.getBigDecimal("price"),r.getInt("chat_credits"),r.getInt("review_credits"),r.getInt("valid_days"),r.getInt("member_days")),planCode);
            if(choices.size()!=1)throw new ServiceException("该套餐暂不可购买");
            Plan p=choices.get(0);
            if(p.price.signum()<=0||p.price.scale()>2||p.validDays<1||p.validDays>3650||p.chatCredits<0||p.chatCredits>1000000||p.reviewCredits<0||p.reviewCredits>1000000||p.memberDays<0||p.memberDays>3650)throw new ServiceException("套餐配置无效");
            String no=id();var time=now();var expiry=time.plusMinutes(15);
            jdbc.update("INSERT INTO lawyer_billing_order(order_no,tenant_id,user_id,plan_code,plan_name,amount,chat_credits,review_credits,valid_days,member_days,status,app_id,idempotency_key,created_at,expires_at) VALUES(?,?,?,?,?,?,?,?,?,?,'PENDING',?,?,?,?)",
                no,t,user,p.code,p.name,p.price,p.chatCredits,p.reviewCredits,p.validDays,p.memberDays,properties.getAppId(),key,time,expiry);
            return new Order(no,t,user,p,"PENDING",properties.getAppId(),null,null,time,expiry,null);
        });
        Objects.requireNonNull(order);
        if(!order.status.equals("PENDING"))return view(order);
        if(!order.expiresAt.isAfter(now()))throw new ServiceException("该订单已超出支付时间，请创建新订单");
        if(order.payUrl==null){
            // Persist the pending order before making an external request; a timeout must never lose it.
            String url=gateway.precreate(order.no,order.plan.name,order.plan.price.toPlainString(),order.expiresAt.format(PAYMENT_TIME));
            jdbc.update("UPDATE lawyer_billing_order SET pay_url=? WHERE order_no=? AND tenant_id=? AND user_id=? AND status='PENDING'",url,order.no,t,user);
        }
        return view(owned(order.no));
    }
    public OrderView order(String no,boolean refresh){
        Order order=owned(no);
        if(refresh&&order.status.equals("PENDING")&&properties.isEnabled()){
            AlipayGateway.Trade trade=gateway.query(no);
            reconcile(order,trade);
            order=owned(no);
        }
        return view(order);
    }
    public OrderView close(String no){
        gateway.requireEnabled();Order order=owned(no);
        if(!order.status.equals("PENDING"))return view(order);
        if(gateway.close(no)){
            transactions.executeWithoutResult(status->{
                Order locked=lockedOrder(no);
                if(locked.status.equals("PENDING"))jdbc.update("UPDATE lawyer_billing_order SET status='CLOSED' WHERE order_no=? AND status='PENDING'",no);
            });
        }else reconcile(order,gateway.query(no));
        return view(owned(no));
    }
    private void reconcile(Order order,AlipayGateway.Trade trade){
        if(!order.no.equals(trade.orderNo()))throw new ServiceException("支付订单校验失败");
        if(Set.of("TRADE_SUCCESS","TRADE_FINISHED").contains(trade.status())){
            paid(order.no,trade.tradeNo(),trade.amount(),trade.buyerPayTime(),order.appId);
        }else if("TRADE_CLOSED".equals(trade.status()))closed(order.no,false);
    }
    public boolean notifyAlipay(Map<String,String> parameters){
        if(!gateway.verify(parameters)||!properties.getAppId().equals(parameters.get("app_id"))||!properties.getSellerId().equals(parameters.get("seller_id")))return false;
        String no=parameters.get("out_trade_no");String status=parameters.get("trade_status");
        if(no==null||!no.matches("[a-f0-9]{32}"))return false;
        if(Set.of("TRADE_SUCCESS","TRADE_FINISHED").contains(status==null?"":status))
            paid(no,parameters.get("trade_no"),parameters.get("total_amount"),parameters.get("gmt_payment"),parameters.get("app_id"));
        else if("TRADE_CLOSED".equals(status)){
            // Amount and app identity are checked even for a close/refund notification.
            transactions.executeWithoutResult(tx->{
                Order order=lockedOrder(no);checkAmount(order,parameters.get("total_amount"),parameters.get("app_id"));
                if(order.tradeNo!=null&&!order.tradeNo.equals(parameters.get("trade_no")))throw new ServiceException("支付流水不一致");
            });
            closed(no,parameters.get("gmt_payment")!=null);
        }
        return true;
    }
    private Order lockedOrder(String no){
        var rows=jdbc.query("SELECT * FROM lawyer_billing_order WHERE order_no=? FOR UPDATE",this::row,no);
        if(rows.size()!=1)throw new ServiceException("支付订单不存在");return rows.get(0);
    }
    private void checkAmount(Order order,String amount,String app){
        try{if(!order.appId.equals(app)||new BigDecimal(amount).compareTo(order.plan.price)!=0)throw new IllegalArgumentException();}
        catch(Exception e){throw new ServiceException("支付金额或应用校验失败");}
    }
    private void paid(String no,String tradeNo,String amount,String time,String app){
        if(tradeNo==null||!tradeNo.matches("[0-9A-Za-z_-]{8,80}"))throw new ServiceException("支付流水校验失败");
        LocalDateTime paidAt;
        try{paidAt=LocalDateTime.parse(time,PAYMENT_TIME);}catch(Exception e){throw new ServiceException("支付时间校验失败");}
        transactions.executeWithoutResult(tx->{
            Order order=lockedOrder(no);checkAmount(order,amount,app);
            if(order.status.equals("PAID")){
                if(!tradeNo.equals(order.tradeNo))throw new ServiceException("支付流水不一致");return;
            }
            // A previously verified provider close/refund cannot be resurrected by an older success callback.
            if(order.status.equals("CLOSED")||order.status.equals("REFUNDED"))return;
            if(paidAt.isBefore(order.createdAt.minusMinutes(5))||paidAt.isAfter(order.expiresAt)||paidAt.isAfter(now().plusMinutes(10)))throw new ServiceException("支付时间超出订单有效期");
            lockAccount(order.tenant,order.user);
            int changed=jdbc.update("UPDATE lawyer_billing_order SET status='PAID',trade_no=?,paid_at=? WHERE order_no=? AND status='PENDING'",tradeNo,paidAt,no);
            if(changed!=1)throw new ServiceException("订单状态已变化，请刷新");
            String grant=id();Plan p=order.plan;
            jdbc.update("INSERT INTO lawyer_billing_grant(id,tenant_id,user_id,order_no,remaining_chat,remaining_review,member_days,granted_at,expires_at) VALUES(?,?,?,?,?,?,?,?,?)",grant,order.tenant,order.user,no,p.chatCredits,p.reviewCredits,p.memberDays,paidAt,paidAt.plusDays(p.validDays));
            ledger(order.tenant,order.user,grant,no,"PURCHASE",p.chatCredits,p.reviewCredits,null,"支付成功发放权益");
            recomputeMembership(order.tenant,order.user);
        });
    }
    private void closed(String no,boolean paidBeforeClose){
        transactions.executeWithoutResult(tx->{
            Order order=lockedOrder(no);
            if(order.status.equals("REFUNDED"))return;
            if(order.status.equals("PAID")){
                lockAccount(order.tenant,order.user);
                var grants=jdbc.queryForList("SELECT id,remaining_chat,remaining_review FROM lawyer_billing_grant WHERE order_no=? AND revoked=0",no);
                for(var grant:grants){
                    jdbc.update("UPDATE lawyer_billing_grant SET remaining_chat=0,remaining_review=0,revoked=1 WHERE id=?",grant.get("id"));
                    ledger(order.tenant,order.user,grant.get("id").toString(),no,"REFUND",-((Number)grant.get("remaining_chat")).intValue(),-((Number)grant.get("remaining_review")).intValue(),null,"渠道确认全额退款，撤销未消费权益");
                }
                jdbc.update("UPDATE lawyer_billing_order SET status='REFUNDED' WHERE order_no=?",no);
                recomputeMembership(order.tenant,order.user);
            }else if(order.status.equals("PENDING"))jdbc.update("UPDATE lawyer_billing_order SET status=? WHERE order_no=? AND status='PENDING'",paidBeforeClose?"REFUNDED":"CLOSED",no);
        });
    }
    private void lockAccount(String t,long user){
        jdbc.update("INSERT INTO lawyer_billing_account(tenant_id,user_id) VALUES(?,?) ON DUPLICATE KEY UPDATE user_id=lawyer_billing_account.user_id",t,user);
        jdbc.queryForList("SELECT user_id FROM lawyer_billing_account WHERE tenant_id=? AND user_id=? FOR UPDATE",t,user);
    }
    public Account account(){return transactions.execute(tx->readAccount(tenant(),owner()));}
    private Account readAccount(String t,long user){
        var totals=jdbc.queryForMap("SELECT COALESCE(SUM(remaining_chat),0) chat,COALESCE(SUM(remaining_review),0) review FROM lawyer_billing_grant WHERE tenant_id=? AND user_id=? AND revoked=0 AND expires_at>?",t,user,now());
        var membership=jdbc.query("SELECT member_until FROM lawyer_billing_account WHERE tenant_id=? AND user_id=?",(r,n)->date(r,"member_until"),t,user);
        LocalDateTime until=membership.isEmpty()?null:membership.get(0);
        return new Account(((Number)totals.get("chat")).longValue(),((Number)totals.get("review")).longValue(),until,until!=null&&until.isAfter(now()));
    }
    /** Joins the first effective message transaction. False falls back to the daily free quota. */
    public boolean consumeEntitlement(String t,long user,String kind){
        if(!properties.isEnabled())return true;
        if(!Set.of("CHAT","REVIEW").contains(kind))throw new ServiceException("用量类型无效");
        return Boolean.TRUE.equals(transactions.execute(tx->{
            lockAccount(t,user);
            if(debit(t,user,kind,1,null,"CONSUME","开始处理"+(kind.equals("REVIEW")?"审查":"咨询")+"会话首条有效消息"))return true;
            return readAccount(t,user).memberActive;
        }));
    }
    private boolean debit(String t,long user,String kind,int count,Long actor,String operation,String reason){
        String column=kind.equals("REVIEW")?"remaining_review":"remaining_chat";
        var grants=jdbc.queryForList("SELECT id,"+column+" remaining FROM lawyer_billing_grant WHERE tenant_id=? AND user_id=? AND revoked=0 AND expires_at>? AND "+column+">0 ORDER BY expires_at,granted_at,id FOR UPDATE",t,user,now());
        long available=grants.stream().mapToLong(g->((Number)g.get("remaining")).longValue()).sum();
        if(available<count)return false;
        int needed=count;
        for(var grant:grants){
            int take=Math.min(needed,((Number)grant.get("remaining")).intValue());
            if(take==0)break;
            if(jdbc.update("UPDATE lawyer_billing_grant SET "+column+"="+column+"-? WHERE id=? AND tenant_id=? AND user_id=? AND "+column+">=?",take,grant.get("id"),t,user,take)!=1)throw new ServiceException("额度状态已变化，请重试");
            ledger(t,user,grant.get("id").toString(),null,operation,kind.equals("CHAT")?-take:0,kind.equals("REVIEW")?-take:0,actor,reason);
            needed-=take;
        }
        return true;
    }
    private void recomputeMembership(String t,long user){
        var rows=jdbc.query("SELECT granted_at,member_days FROM lawyer_billing_grant WHERE tenant_id=? AND user_id=? AND revoked=0 AND member_days>0 ORDER BY granted_at,id",(r,n)->new MemberGrant(date(r,"granted_at"),r.getInt("member_days")),t,user);
        LocalDateTime until=null;
        for(var row:rows){
            var start=row.grantedAt;
            until=(until==null||until.isBefore(start)?start:until).plusDays(row.days);
        }
        jdbc.update("UPDATE lawyer_billing_account SET member_until=? WHERE tenant_id=? AND user_id=?",until,t,user);
    }
    private void ledger(String t,long user,String grant,String order,String kind,int chat,int review,Long actor,String reason){
        jdbc.update("INSERT INTO lawyer_billing_ledger(id,tenant_id,user_id,grant_id,order_no,kind,chat_delta,review_delta,actor_user_id,note,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",id(),t,user,grant,order,kind,chat,review,actor,reason,now());
    }
    public void adjust(long user,int chat,int review,int validDays,String reason){
        if(!properties.isEnabled())throw new ServiceException("本系统为本地部署，不按次计费");
        String t=tenant();long actor=owner();
        if(Math.abs((long)chat)>1000000||Math.abs((long)review)>1000000||validDays<1||validDays>3650||reason==null||reason.isBlank()||reason.length()>200||chat==0&&review==0)throw new ServiceException("额度调整参数无效");
        transactions.executeWithoutResult(tx->{
            Integer exists=jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE user_id=? AND tenant_id=? AND del_flag='0'",Integer.class,user,t);
            if(exists==null||exists!=1)throw new ServiceException("用户不存在",404);
            lockAccount(t,user);
            if(chat<0&&!debit(t,user,"CHAT",-chat,actor,"ADJUST",reason))throw new ServiceException("可扣减咨询次数不足");
            if(review<0&&!debit(t,user,"REVIEW",-review,actor,"ADJUST",reason))throw new ServiceException("可扣减审查次数不足");
            if(chat>0||review>0){
                String grant=id();var time=now();
                jdbc.update("INSERT INTO lawyer_billing_grant(id,tenant_id,user_id,remaining_chat,remaining_review,granted_at,expires_at) VALUES(?,?,?,?,?,?,?)",grant,t,user,Math.max(chat,0),Math.max(review,0),time,time.plusDays(validDays));
                ledger(t,user,grant,null,"ADJUST",Math.max(chat,0),Math.max(review,0),actor,reason);
            }
        });
    }
    public void requireReviewSession(String t,long user,long session){
        var kinds=jdbc.queryForList("SELECT usage_kind FROM chat_session WHERE id=? AND tenant_id=? AND user_id=?",String.class,session,t,user);
        if(kinds.size()!=1||!"REVIEW".equals(kinds.get(0)))throw new ServiceException("请通过合同审查入口新建审查会话，以使用对应审查额度",409);
    }
}
