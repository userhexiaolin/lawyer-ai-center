package org.ruoyi.service.lawyer;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.service.OssService;
import org.ruoyi.common.mybatis.core.page.TableDataInfo;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.bo.lawyer.CaseInput;
import org.ruoyi.domain.entity.lawyer.LawyerCase;
import org.ruoyi.domain.entity.chat.ChatSession;
import org.ruoyi.domain.vo.chat.ChatSessionVo;
import org.ruoyi.mapper.lawyer.LawyerCaseMapper;
import org.ruoyi.mapper.chat.ChatSessionMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.time.LocalDateTime;
import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.annotation.JsonFormat;

@Service
@RequiredArgsConstructor
public class LawyerCaseService {
    private final LawyerCaseMapper mapper;
    private final CaseAccess access;
    private final ChatSessionMapper sessions;
    private final JdbcTemplate jdbc;
    private final OssService oss;
    public record Material(@JsonFormat(shape=JsonFormat.Shape.STRING) Long ossId,String fileName,String originalName,
        String fileSuffix,String url,LocalDateTime uploadedAt,LocalDateTime linkedAt,String downloadUrl) {}
    public record CaseDetail(LawyerCase caseInfo,List<ChatSessionVo> sessions,List<Material> files) {}
    public List<CaseReminder> upcoming() {
        var today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        var values=mapper.selectList(Wrappers.<LawyerCase>lambdaQuery().eq(LawyerCase::getTenantId,LoginHelper.getTenantId()).eq(LawyerCase::getUserId,LoginHelper.getUserId()).eq(LawyerCase::getStatus,"进行中")
            .and(q->q.between(LawyerCase::getNextHearingDate,today,today.plusDays(7)).or().between(LawyerCase::getLimitationDate,today,today.plusDays(7))));
        return CaseReminder.from(values,today);
    }
    public TableDataInfo<LawyerCase> list(String keyword,String status,String caseType,int page,int size) {
        var query=new QueryWrapper<LawyerCase>().eq("tenant_id",LoginHelper.getTenantId()).eq("user_id",LoginHelper.getUserId());
        if(status!=null&&!status.isBlank())query.eq("status",status);
        if(caseType!=null&&!caseType.isBlank())query.like("case_type",caseType);
        if(keyword!=null&&!keyword.isBlank())query.and(q->q.like("title",keyword).or().like("client_name",keyword).or().like("case_type",keyword));
        query.orderByAsc("LEAST(COALESCE(next_hearing_date,'9999-12-31'),COALESCE(limitation_date,'9999-12-31'))").orderByDesc("id");
        return TableDataInfo.build(mapper.selectPage(new Page<>(Math.max(1,page),Math.max(1,Math.min(100,size))),query));
    }
    public CaseDetail detail(Long id) {
        var value=access.owned(id);
        var linked=sessions.selectVoList(Wrappers.<ChatSession>lambdaQuery().eq(ChatSession::getUserId,LoginHelper.getUserId()).eq(ChatSession::getCaseId,id).orderByDesc(ChatSession::getId));
        return new CaseDetail(value,linked,materials(id,null));
    }
    /** Existing file associations are the one canonical material archive, including old uploads. */
    public List<Material> files(Long caseId) { access.owned(caseId);return materials(caseId,null); }
    public Material file(Long caseId,Long ossId) {
        access.owned(caseId);var values=materials(caseId,ossId);
        if(values.isEmpty())throw new ServiceException("材料不存在或无访问权限",404);return values.get(0);
    }
    private List<Material> materials(Long caseId,Long ossId) {
        String sql="SELECT o.oss_id,o.file_name,o.original_name,o.file_suffix,o.create_time,f.created_at FROM lawyer_case_file f JOIN sys_oss o ON o.oss_id=f.oss_id AND o.tenant_id=f.tenant_id AND o.create_by=f.user_id WHERE f.case_id=? AND f.tenant_id=? AND f.user_id=?";
        List<Object> args=new ArrayList<>(List.of(caseId,LoginHelper.getTenantId(),LoginHelper.getUserId()));
        if(ossId!=null){sql+=" AND f.oss_id=?";args.add(ossId);}sql+=" ORDER BY o.create_time DESC,o.oss_id DESC";
        var rows=jdbc.query(sql,(r,n)->new Material(r.getLong("oss_id"),r.getString("file_name"),r.getString("original_name"),r.getString("file_suffix"),null,
            r.getTimestamp("create_time")==null?null:r.getTimestamp("create_time").toLocalDateTime(),r.getTimestamp("created_at").toLocalDateTime(),"/lawyer/case/"+caseId+"/files/"+r.getLong("oss_id")+"/download"),args.toArray());
        if(rows.isEmpty())return rows;
        // Do not disclose transferable OSS URLs; both old and new material views use the case-authenticated stream.
        return rows.stream().map(r->new Material(r.ossId(),r.fileName(),r.originalName(),r.fileSuffix(),r.downloadUrl(),r.uploadedAt(),r.linkedAt(),r.downloadUrl())).toList();
    }
    public void download(Long caseId,Long ossId,HttpServletResponse response) throws IOException {
        access.owned(caseId);
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM lawyer_case_file f JOIN sys_oss o ON o.oss_id=f.oss_id AND o.tenant_id=f.tenant_id AND o.create_by=f.user_id WHERE f.case_id=? AND f.oss_id=? AND f.tenant_id=? AND f.user_id=?",Long.class,caseId,ossId,LoginHelper.getTenantId(),LoginHelper.getUserId());
        if(count==null||count!=1)throw new ServiceException("材料不存在或无访问权限",404);
        oss.downloadFile(ossId,response);
    }
    private void apply(LawyerCase value,CaseInput input) {
        value.setTitle(input.title().strip()); value.setCaseType(input.caseType()); value.setClientName(input.clientName());
        value.setOppositeParty(input.oppositeParty()); value.setStatus(input.status()==null?"进行中":input.status()); value.setCourt(input.court());
        value.setFilingDate(input.filingDate()); value.setNextHearingDate(input.nextHearingDate()); value.setLimitationDate(input.limitationDate()); value.setDescription(input.description());
    }
    @Transactional(rollbackFor=Exception.class)
    public Long create(CaseInput input) {
        var value=new LawyerCase(); apply(value,input); value.setUserId(LoginHelper.getUserId()); value.setTenantId(LoginHelper.getTenantId()); value.setDeleted(0);
        if(mapper.insert(value)!=1)throw new ServiceException("案件创建失败，请重试");return value.getId();
    }
    @Transactional(rollbackFor=Exception.class)
    public void update(CaseInput input) {
        var value=access.owned(input.id(),true); apply(value,input);
        // Explicit SET permits clearing optional dates and names without changing ownership.
        mapper.update(null,Wrappers.<LawyerCase>lambdaUpdate().eq(LawyerCase::getId,value.getId()).eq(LawyerCase::getUserId,value.getUserId()).eq(LawyerCase::getTenantId,value.getTenantId())
            .set(LawyerCase::getTitle,value.getTitle()).set(LawyerCase::getCaseType,value.getCaseType()).set(LawyerCase::getClientName,value.getClientName()).set(LawyerCase::getOppositeParty,value.getOppositeParty())
            .set(LawyerCase::getStatus,value.getStatus()).set(LawyerCase::getCourt,value.getCourt()).set(LawyerCase::getFilingDate,value.getFilingDate()).set(LawyerCase::getNextHearingDate,value.getNextHearingDate())
            .set(LawyerCase::getLimitationDate,value.getLimitationDate()).set(LawyerCase::getDescription,value.getDescription()).set(LawyerCase::getUpdateTime,new Date()).set(LawyerCase::getUpdateBy,LoginHelper.getUserId()));
    }
    @Transactional(rollbackFor=Exception.class)
    public void delete(Long id) {
        var value=access.owned(id,true);
        sessions.update(null,Wrappers.<ChatSession>lambdaUpdate().eq(ChatSession::getUserId,value.getUserId()).eq(ChatSession::getCaseId,id).set(ChatSession::getCaseId,null));
        jdbc.update("DELETE FROM lawyer_case_file WHERE tenant_id=? AND user_id=? AND case_id=?",value.getTenantId(),value.getUserId(),id);
        jdbc.update("UPDATE lawyer_work_time SET case_id=NULL WHERE tenant_id=? AND user_id=? AND case_id=?",value.getTenantId(),value.getUserId(),id);
        mapper.deleteById(value.getId());
    }
    private ChatSession ownedSession(Long sessionId) {
        if(sessionId==null)throw new ServiceException("会话不可用",404);
        var value=sessions.selectOne(Wrappers.<ChatSession>lambdaQuery().eq(ChatSession::getId,sessionId).eq(ChatSession::getUserId,LoginHelper.getUserId()));
        if(value==null)throw new ServiceException("会话不可用",404);return value;
    }
    @Transactional(rollbackFor=Exception.class)
    public void linkSession(Long caseId,Long sessionId) {
        access.owned(caseId,true);ownedSession(sessionId);
        sessions.update(null,Wrappers.<ChatSession>lambdaUpdate().eq(ChatSession::getId,sessionId).eq(ChatSession::getUserId,LoginHelper.getUserId()).set(ChatSession::getCaseId,caseId));
    }
    @Transactional(rollbackFor=Exception.class)
    public void unlinkSession(Long caseId,Long sessionId) {
        access.owned(caseId,true);ownedSession(sessionId);
        sessions.update(null,Wrappers.<ChatSession>lambdaUpdate().eq(ChatSession::getId,sessionId).eq(ChatSession::getUserId,LoginHelper.getUserId()).eq(ChatSession::getCaseId,caseId).set(ChatSession::getCaseId,null));
    }
    @Transactional(rollbackFor=Exception.class)
    public void linkFile(Long caseId,Long ossId) {
        var value=access.owned(caseId,true);
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM sys_oss WHERE oss_id=? AND tenant_id=? AND create_by=?",Long.class,ossId,value.getTenantId(),value.getUserId());
        if(count==null||count!=1L)throw new ServiceException("文件不可用，请重新上传本账号的材料",404);
        jdbc.update("INSERT IGNORE INTO lawyer_case_file(tenant_id,user_id,case_id,oss_id) VALUES(?,?,?,?)",value.getTenantId(),value.getUserId(),caseId,ossId);
    }
    @Transactional(rollbackFor=Exception.class)
    public void unlinkFile(Long caseId,Long ossId) {
        var value=access.owned(caseId,true);
        jdbc.update("DELETE FROM lawyer_case_file WHERE tenant_id=? AND user_id=? AND case_id=? AND oss_id=?",value.getTenantId(),value.getUserId(),caseId,ossId);
    }
}
