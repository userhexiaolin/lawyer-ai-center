package org.ruoyi.service.legal;

import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.entity.legal.LegalDocTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;

/** Persistent progress for the existing authenticated parsing request; no detached login context. */
@Service
public class TemplateFieldTask {
    private final JdbcTemplate jdbc;
    private final DocTemplateService templates;
    private final TemplateFiles files;
    private final TemplateFieldAnalysis analysis;
    private final TransactionTemplate transactions;
    public TemplateFieldTask(JdbcTemplate jdbc,DocTemplateService templates,TemplateFiles files,TemplateFieldAnalysis analysis,PlatformTransactionManager manager){
        this.jdbc=jdbc;this.templates=templates;this.files=files;this.analysis=analysis;this.transactions=new TransactionTemplate(manager);
    }
    public record Status(String taskId,String state,String phase,String warning,String resultVersion,String startedAt,String expiresAt){}
    private record Task(Status status,String revision,String source,String type){}
    private record Claim(Task task,boolean acquired){}
    private String tenant(){String tenant=LoginHelper.getTenantId();if(tenant==null||LoginHelper.getUserId()==null)throw new DraftingException("请先登录",401);return tenant;}
    private List<Task> read(Long id){
        return jdbc.query("SELECT task_id,state,phase,warning,result_version,created_at,expires_at,expected_revision,source_version,document_type FROM legal_doc_template_field_task WHERE tenant_id=? AND template_id=?",(rs,i)->new Task(new Status(rs.getString("task_id"),rs.getString("state"),rs.getString("phase"),rs.getString("warning"),rs.getString("result_version"),rs.getTimestamp("created_at").toInstant().toString(),rs.getTimestamp("expires_at").toInstant().toString()),rs.getString("expected_revision"),rs.getString("source_version"),rs.getString("document_type")),tenant(),id);
    }
    public Status status(Long id){
        templates.typeSnapshot(id);
        expire(List.of(id));var rows=read(id);return rows.isEmpty()?null:rows.get(0).status();
    }
    private void expire(Collection<Long> ids){
        if(ids.isEmpty())return;
        var args=new ArrayList<Object>();args.add(tenant());args.addAll(ids);
        jdbc.update("UPDATE legal_doc_template_field_task SET state='failed',phase='解析已中断或超时',warning='任务未在期限内完成，请重试；已保存的字段配置保持不变。',updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=? AND template_id IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+") AND state='running' AND expires_at<CURRENT_TIMESTAMP(6)",args.toArray());
    }
    public void decorate(List<LegalDocTemplate> rows){
        var ids=rows.stream().map(LegalDocTemplate::getId).filter(Objects::nonNull).toList();if(ids.isEmpty())return;expire(ids);
        var args=new ArrayList<Object>();args.add(tenant());args.addAll(ids);
        var statuses=new HashMap<Long,Status>();
        jdbc.query("SELECT template_id,task_id,state,phase,warning,result_version,created_at,expires_at FROM legal_doc_template_field_task WHERE tenant_id=? AND template_id IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")",(org.springframework.jdbc.core.RowCallbackHandler)rs->statuses.put(rs.getLong("template_id"),new Status(rs.getString("task_id"),rs.getString("state"),rs.getString("phase"),rs.getString("warning"),rs.getString("result_version"),rs.getTimestamp("created_at").toInstant().toString(),rs.getTimestamp("expires_at").toInstant().toString())),args.toArray());
        rows.forEach(row->row.setFieldAnalysis(statuses.get(row.getId())));
    }
    public Status start(Long id,boolean replacePending){
        String scope=tenant();expire(List.of(id));
        Claim claim=transactions.execute(tx->{
            var snapshot=templates.typeSnapshot(id);templates.lockRevision(id,snapshot.revision());
            if(snapshot.sourceVersion()==null)throw new DraftingException("请先上传模板原文件",422);
            var old=read(id);
            if(!old.isEmpty()&&"running".equals(old.get(0).status().state()))return new Claim(old.get(0),false);
            if(!replacePending&&!old.isEmpty()&&Set.of("ready","partial").contains(old.get(0).status().state())&&snapshot.revision().equals(old.get(0).revision()))throw new DraftingException("已有待核对的解析结果，请先编辑字段，或确认重新解析",409);
            String job=UUID.randomUUID().toString();
            jdbc.update("INSERT INTO legal_doc_template_field_task(tenant_id,template_id,task_id,uploaded_by,source_version,expected_revision,document_type,state,phase,warning,result_version,created_at,updated_at,expires_at) VALUES (?,?,?,?,?,?,?,'running','正在解析原文件','',NULL,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6),DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 11 MINUTE)) ON DUPLICATE KEY UPDATE task_id=VALUES(task_id),uploaded_by=VALUES(uploaded_by),source_version=VALUES(source_version),expected_revision=VALUES(expected_revision),document_type=VALUES(document_type),state=VALUES(state),phase=VALUES(phase),warning='',result_version=NULL,created_at=VALUES(created_at),updated_at=VALUES(updated_at),expires_at=VALUES(expires_at)",scope,id,job,LoginHelper.getUserId(),snapshot.sourceVersion(),snapshot.revision(),snapshot.documentType());
            return new Claim(read(id).get(0),true);
        });
        if(claim==null)throw new IllegalStateException("Missing task claim");
        if(!claim.acquired())return claim.task().status();
        var task=claim.task();String job=task.status().taskId();
        try{
            var working=templates.parse(task.source(),task.type());
            phase(id,job,"正在 AI 识别字段");
            var result=analysis.analyze(working.sourceVersion());
            phase(id,job,"正在保存字段建议");
            transactions.executeWithoutResult(tx->{
                templates.lockRevision(id,task.revision());
                String state="AI".equals(result.method())?"ready":"partial";
                int count=jdbc.update("UPDATE legal_doc_template_field_task SET state=?,phase=?,warning=?,result_version=?,updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=? AND template_id=? AND task_id=? AND state='running' AND expires_at>=CURRENT_TIMESTAMP(6)",state,"AI".equals(result.method())?"解析完成，待人工核对":"规则候选待核对",result.warning(),working.sourceVersion(),scope,id,job);
                if(count!=1)throw new DraftingException("解析任务已过期，请刷新后重试",409);
            });
        }catch(Exception failure){
            boolean stale=failure instanceof DraftingException e&&e.getCode()==409;
            jdbc.update("UPDATE legal_doc_template_field_task SET state=?,phase=?,warning=?,updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=? AND template_id=? AND task_id=? AND state='running'",stale?"stale":"failed",stale?"模板已变化":"解析失败",stale?"模板或原文件已变化，本次结果未覆盖已保存字段，请刷新后重试。":"文件解析未完成，请检查文件或转换服务后重试；已保存字段保持不变。",scope,id,job);
        }
        return read(id).get(0).status();
    }
    private void phase(Long id,String job,String phase){
        if(jdbc.update("UPDATE legal_doc_template_field_task SET phase=?,updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=? AND template_id=? AND task_id=? AND state='running' AND expires_at>=CURRENT_TIMESTAMP(6)",phase,tenant(),id,job)!=1)throw new DraftingException("解析任务已过期，请刷新后重试",409);
    }
    public LegalDocTemplate result(Long id){
        return transactions.execute(tx->{
            var row=templates.get(id);var records=read(id);
            if(records.isEmpty()||!Set.of("ready","partial").contains(records.get(0).status().state()))return row;
            var task=records.get(0);templates.lockRevision(id,task.revision());
            var config=files.analysisConfiguration(files.analysisWorkingCopy(task.status().resultVersion()));
            row.setSourceVersion(config.version());row.setSourceFilename(config.filename());row.setSourceFormat(config.format());row.setParseStatus(config.parseStatus());row.setAnalysisWarning(config.warning());row.setFieldsConfirmed(false);row.setBlocks(config.blocks());row.setContent(config.blocks().stream().map(TemplateLayout.Block::text).reduce((a,b)->a+"\n"+b).orElse(""));row.setEditableBlocks(config.blocks().stream().map(TemplateLayout.Block::id).toList());row.setExpectedRevision(task.revision());return row;
        });
    }
}
