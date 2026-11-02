package org.ruoyi.service.lawyer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.domain.entity.lawyer.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;

/** Durable DB queue; bounded execution. A fenced lease prevents a resumed worker overwriting a newer attempt. */
@Slf4j @Component @RequiredArgsConstructor
public class DossierParseWorker {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final DossierMaterialService materials;
    private final DossierFileReader reader;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private List<DossierJobHandler> handlers=List.of();
    public void handlers(List<DossierJobHandler> value){handlers=List.copyOf(value);}
    @Value("${legal.dossier.enabled:false}") private boolean enabled;
    private final ExecutorService pool=Executors.newFixedThreadPool(2,r->{var t=new Thread(r,"dossier-parse");t.setDaemon(true);return t;});
    private final Semaphore capacity=new Semaphore(2);
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"dossier-queue");t.setDaemon(true);return t;});
    private volatile boolean closed;
    private static final ObjectMapper JSON=new ObjectMapper();
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void start(){if(enabled)timer.scheduleWithFixedDelay(this::poll,0,2,TimeUnit.SECONDS);}
    public void poll(){
        if(!enabled||closed)return;
        try {
            // Do not query ahead into an unbounded in-memory queue. The database remains the queue.
            for(int i=0;i<2&&capacity.tryAcquire();i++){
                boolean submitted=false;
                try{
                var rows=jdbc.queryForList("SELECT j.id FROM lawyer_case_parse_job j WHERE (j.state='queued' OR (j.state='running' AND j.lease_until<UTC_TIMESTAMP())) AND NOT EXISTS(SELECT 1 FROM lawyer_case_parse_job b WHERE b.case_id=j.case_id AND b.tenant_id=j.tenant_id AND b.id<>j.id AND b.state='running' AND b.lease_until>=UTC_TIMESTAMP()) ORDER BY j.create_time,j.id LIMIT 1",Long.class);
                if(rows.isEmpty())break;
                long jobId=rows.get(0);String lease=UUID.randomUUID().toString();
                if(!claim(jobId,lease))break;
                pool.execute(()->{try{run(jobId,lease);}finally{capacity.release();}});
                submitted=true;
                }finally{if(!submitted)capacity.release();}
            }
        }catch(Exception e){log.warn("Dossier queue unavailable; errorType={}",e.getClass().getSimpleName());}
    }
    public boolean claim(long jobId,String lease){
        return Boolean.TRUE.equals(tx.execute(status->{
            var jobs=jdbc.query("SELECT * FROM lawyer_case_parse_job WHERE id=?",DossierMaterialService.mapping(CaseParseJob.class),jobId);
            if(jobs.isEmpty())return false;var j=jobs.get(0);
            if(jdbc.queryForList("SELECT id FROM lawyer_case WHERE id=? AND tenant_id=? AND user_id=? AND deleted=0 FOR UPDATE",j.getCaseId(),j.getTenantId(),j.getUserId()).isEmpty()){
                int cancelled=jdbc.update("UPDATE lawyer_case_parse_job SET state='cancelled',error_code='ACCESS_REVOKED',error_message='案件权限已失效',lease_token=NULL,lease_until=NULL,update_time=UTC_TIMESTAMP() WHERE id=? AND (state='queued' OR (state='running' AND lease_until<UTC_TIMESTAMP()))",jobId);
                if(cancelled==1 && "parse".equals(j.getJobType())){
                    jdbc.update("UPDATE lawyer_case_file_page SET extract_status='cancelled',error_code='ACCESS_REVOKED',error_message='案件权限已失效，已停止解析',version=version+1,update_time=UTC_TIMESTAMP() WHERE file_version_id=? AND extract_status IN ('queued','running')",j.getFileVersionId());
                    jdbc.update("UPDATE lawyer_case_file_version SET error_code='ACCESS_REVOKED',error_message='案件权限已失效，已停止解析' WHERE id=?",j.getFileVersionId());
                    materials.aggregate(j.getFileVersionId(),"cancelled");
                    materials.log(j.getCaseId(),j.getFileVersionId(),jobId,null,"parse_cancelled",j.getState(),"cancelled","ACCESS_REVOKED",j.getUserId());
                }
                return false;
            }
            Integer running=jdbc.queryForObject("SELECT COUNT(*) FROM lawyer_case_parse_job WHERE tenant_id=? AND case_id=? AND id<>? AND state='running' AND lease_until>=UTC_TIMESTAMP()",Integer.class,j.getTenantId(),j.getCaseId(),jobId);
            if(running!=null&&running>0)return false;
            return jdbc.update("UPDATE lawyer_case_parse_job SET state='running',attempts=attempts+1,lease_token=?,lease_until=DATE_ADD(UTC_TIMESTAMP(),INTERVAL 180 SECOND),update_time=UTC_TIMESTAMP() WHERE id=? AND (state='queued' OR (state='running' AND lease_until<UTC_TIMESTAMP()))",lease,jobId)==1;
        }));
    }
    private boolean alive(long jobId,String lease){
        return jdbc.update("UPDATE lawyer_case_parse_job SET lease_until=DATE_ADD(UTC_TIMESTAMP(),INTERVAL 180 SECOND),update_time=UTC_TIMESTAMP() WHERE id=? AND lease_token=? AND state='running'",jobId,lease)==1;
    }
    private boolean fence(long jobId,String lease){
        return !jdbc.queryForList("SELECT id FROM lawyer_case_parse_job WHERE id=? AND lease_token=? AND state='running' FOR UPDATE",jobId,lease).isEmpty();
    }
    public void run(long jobId,String lease){
        CaseParseJob job=null;
        ScheduledFuture<?> heartbeat=null;
        try {
            job=jdbc.queryForObject("SELECT * FROM lawyer_case_parse_job WHERE id=?",DossierMaterialService.mapping(CaseParseJob.class),jobId);
            if(job==null||!alive(jobId,lease))return;
            // Conversion and OCR can outlive a page boundary. Keep the fenced lease alive
            // while they run so another process does not repeat an otherwise healthy job.
            heartbeat=timer.scheduleWithFixedDelay(()->{try{alive(jobId,lease);}catch(Exception e){log.warn("Dossier lease renewal unavailable; jobId={}; errorType={}",jobId,e.getClass().getSimpleName());}},30,30,TimeUnit.SECONDS);
            if(!"parse".equals(job.getJobType())){
                var dispatch=job;
                var handler=handlers.stream().filter(h->h.supports(dispatch.getJobType())).findFirst().orElseThrow(()->new DossierReadException("HANDLER_UNAVAILABLE","任务处理器尚不可用，请联系管理员"));
                handler.execute(job,lease);return;
            }
            var version=jdbc.queryForObject("SELECT * FROM lawyer_case_file_version WHERE id=? AND tenant_id=? AND case_id=? AND user_id=?",DossierMaterialService.mapping(CaseFileVersion.class),job.getFileVersionId(),job.getTenantId(),job.getCaseId(),job.getUserId());
            if(version==null)throw new DossierReadException("VERSION_MISSING","文件版本不存在或权限已失效");
            // Re-check case and object ownership from persistent IDs before any storage access.
            byte[] bytes=materials.checkedBytes(version);
            List<Integer> requested=JSON.readValue(job.getRequestedPages(),new TypeReference<List<Integer>>(){});
            try(var source=reader.open(bytes,version.getMime())){
                CaseParseJob j=job;
                List<Integer> targets=tx.execute(status->{
                    if(!fence(jobId,lease))return List.<Integer>of();
                    if(version.getPageCount()!=null&&version.getPageCount()!=source.count())throw new DossierReadException("PAGE_COUNT_CHANGED","物理页数与既有版本不一致，请重新核对原件");
                    jdbc.update("UPDATE lawyer_case_file_version SET page_count=?,parse_status='running',error_code=NULL,error_message=NULL,update_time=UTC_TIMESTAMP() WHERE id=?",source.count(),version.getId());
                    for(int page=1;page<=source.count();page++)jdbc.update("INSERT IGNORE INTO lawyer_case_file_page(id,tenant_id,case_id,file_version_id,page_index,extract_status,ocr_status,preview_status,extraction_method,parser_version,last_job_id,update_time) VALUES(?,?,?,?,?,'queued','not_started','not_started','unknown',?,?,UTC_TIMESTAMP())",DossierMaterialService.id(),j.getTenantId(),j.getCaseId(),version.getId(),page,DossierFileReader.VERSION,jobId);
                    for(int page=1;page<=source.count();page++)if(source.label(page)!=null)jdbc.update("UPDATE lawyer_case_file_page SET page_label=? WHERE file_version_id=? AND page_index=?",source.label(page),version.getId(),page);
                    var all=jdbc.queryForList("SELECT page_index FROM lawyer_case_file_page WHERE file_version_id=? AND extract_status<>'success' ORDER BY page_index",Integer.class,version.getId());
                    var chosen=requested.isEmpty()?all:all.stream().filter(requested::contains).toList();
                    jdbc.update("UPDATE lawyer_case_parse_job SET progress_total=?,progress_done=0 WHERE id=?",chosen.size(),jobId);
                    materials.log(j.getCaseId(),version.getId(),jobId,null,"parse_running",j.getState(),"running",null,j.getUserId());return chosen;
                });
                for(int page:targets){
                    if(closed||!alive(jobId,lease))return;
                    // Runtime permission can change during a long document. Never rely on the original request thread.
                    Integer allowed=jdbc.queryForObject("SELECT COUNT(*) FROM lawyer_case WHERE id=? AND tenant_id=? AND user_id=? AND deleted=0",Integer.class,j.getCaseId(),j.getTenantId(),j.getUserId());
                    if(allowed==null||allowed!=1)throw new DossierReadException("ACCESS_REVOKED","案件权限已失效，已停止解析");
                    var result=source.read(page);
                    tx.executeWithoutResult(status->{
                        if(!fence(jobId,lease))return;
                        String before=jdbc.queryForObject("SELECT extract_status FROM lawyer_case_file_page WHERE file_version_id=? AND page_index=? FOR UPDATE",String.class,version.getId(),page);
                        // A recovered job skips successes and never overwrites manual blank confirmations.
                        if(!before.equals("success")){
                            jdbc.update("UPDATE lawyer_case_file_page SET extract_status=?,ocr_status=?,preview_status=?,extraction_method=?,text_content=?,char_count=?,text_hash=?,error_code=?,error_message=?,parser_version=?,last_job_id=?,version=version+1,update_time=UTC_TIMESTAMP() WHERE file_version_id=? AND page_index=? AND extract_status<>'success'",result.usable()?"success":"failed",result.ocrStatus(),result.previewStatus(),result.method(),result.text(),result.text().length(),DossierFiles.hash(result.text().getBytes(java.nio.charset.StandardCharsets.UTF_8)),result.errorCode(),result.errorMessage(),DossierFileReader.VERSION,jobId,version.getId(),page);
                            materials.log(j.getCaseId(),version.getId(),jobId,page,"page_parsed",before,result.usable()?"success":"failed",result.errorCode(),j.getUserId());
                        }
                        jdbc.update("UPDATE lawyer_case_parse_job SET progress_done=progress_done+1,lease_until=DATE_ADD(UTC_TIMESTAMP(),INTERVAL 180 SECOND),update_time=UTC_TIMESTAMP() WHERE id=?",jobId);
                        // The aggregate is based only on page states; unrelated AI tasks cannot write this status.
                        materials.aggregate(version.getId(),null);
                        jdbc.update("UPDATE lawyer_case_file_version SET parse_status='running' WHERE id=?",version.getId());
                    });
                }
                tx.executeWithoutResult(status->{
                    if(!fence(jobId,lease))return;
                    var errors=jdbc.queryForList("SELECT error_code,error_message FROM lawyer_case_file_page WHERE file_version_id=? AND extract_status<>'success' ORDER BY page_index LIMIT 1",version.getId());
                    if(!errors.isEmpty())jdbc.update("UPDATE lawyer_case_file_version SET error_code=?,error_message=? WHERE id=?",errors.get(0).get("error_code"),errors.get(0).get("error_message"),version.getId());
                    materials.aggregate(version.getId(),null);
                    String state=jdbc.queryForObject("SELECT parse_status FROM lawyer_case_file_version WHERE id=?",String.class,version.getId());
                    String error=state.equals("success")?null:"UNREAD_PAGES",message=state.equals("success")?null:"仍有不可读取页，请查看页级错误并重试或人工核对";
                    jdbc.update("UPDATE lawyer_case_parse_job SET state=?,error_code=?,error_message=?,lease_token=NULL,lease_until=NULL,update_time=UTC_TIMESTAMP() WHERE id=?",state,error,message,jobId);
                    materials.log(j.getCaseId(),version.getId(),jobId,null,"parse_finished","running",state,error,j.getUserId());
                });
            }
        }catch(Exception e){
            if(job==null)return;
            String code=e instanceof DossierReadException d?d.errorCode():"PARSE_UNAVAILABLE";
            String message=e instanceof DossierReadException d?d.getMessage():"文件处理暂不可用，请重试；原件与已读取页均保留";
            var j=job;
            try{tx.executeWithoutResult(status->{
                if(!fence(jobId,lease))return;
                if(!"parse".equals(j.getJobType())){
                    jdbc.update("UPDATE lawyer_case_parse_job SET state='failed',error_code=?,error_message=?,lease_token=NULL,lease_until=NULL,update_time=UTC_TIMESTAMP() WHERE id=?",code,message,jobId);return;
                }
                jdbc.update("UPDATE lawyer_case_file_page SET extract_status='failed',error_code=?,error_message=?,last_job_id=?,version=version+1,update_time=UTC_TIMESTAMP() WHERE file_version_id=? AND extract_status IN ('queued','running')",code,message,jobId,j.getFileVersionId());
                jdbc.update("UPDATE lawyer_case_file_version SET error_code=?,error_message=? WHERE id=? AND tenant_id=? AND case_id=?",code,message,j.getFileVersionId(),j.getTenantId(),j.getCaseId());
                materials.aggregate(j.getFileVersionId(),null);
                String state=jdbc.queryForObject("SELECT parse_status FROM lawyer_case_file_version WHERE id=?",String.class,j.getFileVersionId());
                jdbc.update("UPDATE lawyer_case_parse_job SET state=?,error_code=?,error_message=?,lease_token=NULL,lease_until=NULL,update_time=UTC_TIMESTAMP() WHERE id=?",state,code,message,jobId);
                materials.log(j.getCaseId(),j.getFileVersionId(),jobId,null,"parse_failed","running",state,code,j.getUserId());
            });}catch(Exception persistence){log.warn("Dossier result persistence failed; jobId={}; errorType={}",jobId,persistence.getClass().getSimpleName());}
        }finally{if(heartbeat!=null)heartbeat.cancel(false);}
    }
    @jakarta.annotation.PreDestroy public void close(){closed=true;timer.shutdownNow();pool.shutdownNow();}
}
