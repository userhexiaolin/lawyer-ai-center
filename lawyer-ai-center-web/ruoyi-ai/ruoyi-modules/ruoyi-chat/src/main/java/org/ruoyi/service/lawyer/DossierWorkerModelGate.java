package org.ruoyi.service.lawyer;

import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.domain.entity.lawyer.CaseParseJob;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

/** Background authorization derives identity from a live persisted lease, never thread login impersonation. */
@Component @RequiredArgsConstructor
public class DossierWorkerModelGate {
    private final JdbcTemplate jdbc;
    private final DossierBoundaryStore boundaries;
    public void check(CaseParseJob job,String lease,DossierOutboundPolicy.Scope scope){
        if(job==null||scope==null||lease==null||!Objects.equals(job.getTenantId(),scope.tenantId())||!Objects.equals(job.getCaseId(),scope.caseId())||!Objects.equals(job.getUserId(),scope.userId())||jdbc.queryForList("SELECT j.id FROM lawyer_case_parse_job j JOIN lawyer_case c ON c.id=j.case_id AND c.tenant_id=j.tenant_id AND c.user_id=j.user_id AND c.deleted=0 WHERE j.id=? AND j.tenant_id=? AND j.case_id=? AND j.user_id=? AND j.lease_token=? AND j.state='running' AND j.lease_until>UTC_TIMESTAMP()",job.getId(),scope.tenantId(),scope.caseId(),scope.userId(),lease).isEmpty())throw new ServiceException("后台任务权限或租约已失效",404);
        for(String fingerprint:scope.sourceFingerprints()){
            String[] pieces=fingerprint.split(":",2);if(pieces.length!=2||!pieces[0].matches("[1-9][0-9]{0,18}")||jdbc.queryForList("SELECT v.id FROM lawyer_case_file_version v JOIN lawyer_case_file f ON f.id=v.material_id AND f.current_version_id=v.id AND f.tenant_id=v.tenant_id AND f.case_id=v.case_id AND f.user_id=v.user_id WHERE v.id=? AND v.sha256=? AND v.tenant_id=? AND v.case_id=? AND v.user_id=?",pieces[0],pieces[1],scope.tenantId(),scope.caseId(),scope.userId()).isEmpty())throw new ServiceException("材料版本已变化，请重新建立任务范围",409);
        }
        var decision=DossierOutboundPolicy.evaluate(scope,boundaries.find(scope).orElse(null),Instant.now());
        if(decision!=DossierOutboundPolicy.Decision.ALLOW)throw new ServiceException("材料模型处理未获授权或范围已变化（"+decision.name()+"）",403);
    }
    public <T>T call(CaseParseJob job,String lease,DossierOutboundPolicy.Scope scope,Supplier<T> provider){check(job,lease,scope);return provider.get();}
}
