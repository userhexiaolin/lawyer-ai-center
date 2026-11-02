package org.ruoyi.service.lawyer;

import cn.hutool.core.util.IdUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.service.OssService;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.entity.lawyer.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import java.time.*;

@Service @RequiredArgsConstructor
public class DossierMaterialService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final CaseAccess cases;
    private final OssService oss;
    private final DossierFileReader reader;
    @Value("${legal.dossier.enabled:false}") private boolean enabled;
    private static final ObjectMapper JSON=new ObjectMapper();
    public boolean enabled(){return enabled;}
    public void enabled(boolean value){enabled=value;}
    public static long id(){return IdUtil.getSnowflakeNextId();}
    public static String tenant(){return LoginHelper.getTenantId();}
    public static Long actor(){return LoginHelper.getUserId();}
    public void access(Long caseId){if(!enabled)throw new ServiceException("材料溯源尚未启用；请先完成材料数据库迁移并启用 legal.dossier.enabled",503);cases.owned(caseId);}
    public static <T> RowMapper<T> mapping(Class<T> type) {
        var mapper=BeanPropertyRowMapper.newInstance(type);
        return (r,n)->{
            T value=mapper.mapRow(r,n);
            if(value instanceof org.ruoyi.common.tenant.core.TenantEntity entity){
                var columns=new HashSet<String>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)columns.add(r.getMetaData().getColumnLabel(i).toLowerCase(Locale.ROOT));
                if(columns.contains("create_time")&&r.getObject("create_time")!=null)entity.setCreateTime(Date.from(r.getObject("create_time",LocalDateTime.class).toInstant(ZoneOffset.UTC)));
                if(columns.contains("update_time")&&r.getObject("update_time")!=null)entity.setUpdateTime(Date.from(r.getObject("update_time",LocalDateTime.class).toInstant(ZoneOffset.UTC)));
            }
            return value;
        };
    }
    private <T>T one(String sql,Class<T> type,Object...args){var rows=jdbc.query(sql,mapping(type),args);if(rows.isEmpty())throw new ServiceException("材料不存在或无访问权限",404);return rows.get(0);}
    private CaseFile material(Long caseId,Long id,boolean lock){return one("SELECT * FROM lawyer_case_file WHERE id=? AND case_id=? AND tenant_id=? AND user_id=?"+(lock?" FOR UPDATE":""),CaseFile.class,id,caseId,tenant(),actor());}
    public CaseFileVersion version(Long caseId,Long version){access(caseId);return one("SELECT * FROM lawyer_case_file_version WHERE id=? AND case_id=? AND tenant_id=? AND user_id=?",CaseFileVersion.class,version,caseId,tenant(),actor());}
    private CaseUpload ticket(Long caseId,Long id){return one("SELECT * FROM lawyer_case_upload WHERE id=? AND case_id=? AND tenant_id=? AND user_id=? FOR UPDATE",CaseUpload.class,id,caseId,tenant(),actor());}
    public record Init(String fileName,long fileBytes,String sha256,String idempotencyKey,Long materialId){}
    public record Ticket(CaseUpload upload,List<String> duplicateNames){}
    public record MaterialRow(CaseFile material,CaseFileVersion current){}
    public record PageResult<T>(List<T> rows,long total){}
    public record Overview(String caseTitle,long files,long success,long partial,long failed,long parsedPages,long knownPages,long unknownPageFiles,long issues){}
    public record Sources(Long fileVersionId,String fileName,Integer totalPages,int readablePages,List<CaseFilePage> pages,String parseStatus){}
    private static void key(String key){if(key==null||!key.matches("[A-Za-z0-9_-]{12,64}"))throw new ServiceException("请求幂等标识无效，请重试",400);}
    private void legacy(Long caseId){
        cases.owned(caseId,true);
        var legacy=jdbc.queryForList("SELECT f.oss_id,o.original_name FROM lawyer_case_file f JOIN sys_oss o ON o.oss_id=f.oss_id AND o.tenant_id=f.tenant_id AND o.create_by=f.user_id WHERE f.case_id=? AND f.tenant_id=? AND f.user_id=? AND f.id IS NULL FOR UPDATE",caseId,tenant(),actor());
        for(var row:legacy)jdbc.update("UPDATE lawyer_case_file SET id=?,display_name=? WHERE tenant_id=? AND user_id=? AND case_id=? AND oss_id=? AND id IS NULL",id(),row.get("original_name"),tenant(),actor(),caseId,row.get("oss_id"));
    }
    public List<String> duplicates(Long caseId,String sha){return jdbc.queryForList("SELECT DISTINCT file_name FROM lawyer_case_file_version WHERE tenant_id=? AND case_id=? AND sha256=? LIMIT 10",String.class,tenant(),caseId,sha);}
    private Long requestedUploadTarget(Long caseId,CaseUpload upload){
        Long target=upload.getMaterialId();
        if("completed".equals(upload.getState())&&target!=null&&upload.getFileVersionId()!=null){
            var saved=version(caseId,upload.getFileVersionId());var root=material(caseId,target,false);
            // Completion fills material_id for new materials. Recover the original null target from
            // the immutable first version and archive OSS ID, including tickets already persisted.
            // A replacement of an unparsed legacy material also has V1, but retains a different root OSS ID.
            if(Integer.valueOf(1).equals(saved.getVersionNo())&&Objects.equals(saved.getMaterialId(),target)
                &&Objects.equals(saved.getOssId(),upload.getOssId())&&Objects.equals(root.getOssId(),upload.getOssId()))return null;
        }
        return target;
    }
    public Ticket init(Long caseId,Init input){
        access(caseId);key(input.idempotencyKey());String name=DossierFiles.name(input.fileName());DossierFiles.declared(name,input.fileBytes(),input.sha256());
        return tx.execute(status->{
            legacy(caseId);if(input.materialId()!=null)material(caseId,input.materialId(),true);
            var prior=jdbc.query("SELECT * FROM lawyer_case_upload WHERE tenant_id=? AND case_id=? AND idempotency_key=? FOR UPDATE",mapping(CaseUpload.class),tenant(),caseId,input.idempotencyKey());
            if(!prior.isEmpty()){
                var p=prior.get(0);if(!p.getFileName().equals(name)||p.getFileBytes()!=input.fileBytes()||!p.getSha256().equals(input.sha256())||!Objects.equals(requestedUploadTarget(caseId,p),input.materialId()))throw new ServiceException("同一请求标识对应的文件已变化",409);
                return new Ticket(p,duplicates(caseId,input.sha256()));
            }
            long id=id();jdbc.update("INSERT INTO lawyer_case_upload(id,tenant_id,case_id,user_id,material_id,file_name,file_bytes,sha256,idempotency_key,state,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?,'initialized',UTC_TIMESTAMP(),UTC_TIMESTAMP())",id,tenant(),caseId,actor(),input.materialId(),name,input.fileBytes(),input.sha256(),input.idempotencyKey());
            return new Ticket(ticket(caseId,id),duplicates(caseId,input.sha256()));
        });
    }
    public CaseUpload upload(Long caseId,Long uploadId,MultipartFile file){
        access(caseId);
        CaseUpload value=tx.execute(status->{
            cases.owned(caseId,true);var t=ticket(caseId,uploadId);
            if(Set.of("stored","completed").contains(t.getState()))return t;
            int changed=jdbc.update("UPDATE lawyer_case_upload SET state='uploading',lease_until=DATE_ADD(UTC_TIMESTAMP(),INTERVAL 180 SECOND),update_time=UTC_TIMESTAMP() WHERE id=? AND (state='initialized' OR (state='uploading' AND lease_until<UTC_TIMESTAMP()))",uploadId);
            if(changed!=1)throw new ServiceException("该文件正在上传，请稍后重试同一上传任务",409);return t;
        });
        if(Set.of("stored","completed").contains(value.getState()))return value;
        Long saved=null;
        try {
            if(file.getSize()!=value.getFileBytes()||file.getSize()>DossierFiles.MAX_BYTES)throw new ServiceException("上传大小与原文件不符，请重新选择同一文件",400);
            byte[] bytes=file.getBytes();String mime=DossierFiles.validate(value.getFileName(),bytes);
            if(!DossierFiles.hash(bytes).equals(value.getSha256()))throw new ServiceException("文件哈希校验失败，请重新选择原文件",422);
            saved=oss.uploadFile(new BytesFile(value.getFileName(),mime,bytes)).getOssId();Long stored=saved;
            return tx.execute(status->{cases.owned(caseId,true);var current=ticket(caseId,uploadId);
                if(!current.getState().equals("uploading"))throw new ServiceException("上传任务已变化，请刷新",409);
                jdbc.update("UPDATE lawyer_case_upload SET state='stored',oss_id=?,mime=?,lease_until=NULL,update_time=UTC_TIMESTAMP() WHERE id=?",stored,mime,uploadId);return ticket(caseId,uploadId);});
        }catch(Exception e){
            if(saved!=null){try{oss.deleteFile(saved);}catch(Exception ignored){/* storage lifecycle cleanup can retry the unlinked object */}}
            jdbc.update("UPDATE lawyer_case_upload SET state='initialized',lease_until=NULL,update_time=UTC_TIMESTAMP() WHERE id=? AND state='uploading'",uploadId);
            if(e instanceof ServiceException s)throw s;throw new ServiceException("原件存储失败，尚未归档；请重试上传",503);
        }
    }
    public CaseFileVersion complete(Long caseId,Long uploadId,boolean keepDuplicate){
        access(caseId);
        return tx.execute(status->{
            cases.owned(caseId,true);var t=ticket(caseId,uploadId);
            if(t.getState().equals("completed"))return version(caseId,t.getFileVersionId());
            if(!t.getState().equals("stored"))throw new ServiceException("原件尚未上传完成",409);
            if(!duplicates(caseId,t.getSha256()).isEmpty()&&!keepDuplicate)throw new ServiceException("发现相同内容；确认保留副本后再完成归档",409);
            // Association, immutable version, queued task and ticket completion commit together.
            long materialId=t.getMaterialId()==null?id():t.getMaterialId(), versionId=id();int number=1;
            if(t.getMaterialId()==null)jdbc.update("INSERT INTO lawyer_case_file(tenant_id,user_id,case_id,oss_id,id,display_name,current_version_id) VALUES(?,?,?,?,?,?,?)",tenant(),actor(),caseId,t.getOssId(),materialId,t.getFileName(),versionId);
            else {material(caseId,materialId,true);number=jdbc.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM lawyer_case_file_version WHERE material_id=?",Integer.class,materialId);}
            jdbc.update("INSERT INTO lawyer_case_file_version(id,tenant_id,case_id,user_id,material_id,oss_id,version_no,file_name,sha256,file_bytes,mime,parser_version,create_by,create_time,update_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP(),UTC_TIMESTAMP())",versionId,tenant(),caseId,actor(),materialId,t.getOssId(),number,t.getFileName(),t.getSha256(),t.getFileBytes(),t.getMime(),DossierFileReader.VERSION,actor());
            jdbc.update("UPDATE lawyer_case_file SET current_version_id=?,display_name=? WHERE id=? AND tenant_id=? AND case_id=?",versionId,t.getFileName(),materialId,tenant(),caseId);
            jdbc.update("UPDATE lawyer_case_upload SET state='completed',file_version_id=?,material_id=?,keep_duplicate=?,update_time=UTC_TIMESTAMP() WHERE id=?",versionId,materialId,keepDuplicate,uploadId);
            log(caseId,versionId,null,null,"version_created",null,"not_started",null,actor());
            enqueue(caseId,versionId,"upload_"+uploadId,List.of());return version(caseId,versionId);
        });
    }
    public PageResult<MaterialRow> list(Long caseId,String search,String filter,int page,int size){
        access(caseId);tx.executeWithoutResult(status->legacy(caseId));
        String where=" WHERE f.tenant_id=? AND f.case_id=? AND f.user_id=?";List<Object> args=new ArrayList<>(List.of(tenant(),caseId,actor()));
        if(search!=null&&!search.isBlank()){where+=" AND f.display_name LIKE ?";args.add("%"+search.strip()+"%");}
        if("issues".equals(filter)){where+=" AND (v.page_count IS NULL OR v.failed_pages>0 OR v.parse_status IN ('partial','failed','cancelled'))";}
        else if(filter!=null&&!filter.isBlank()){if(!Set.of("not_started","queued","running","success","partial","failed","cancelled").contains(filter))throw new ServiceException("状态筛选无效",400);where+=" AND COALESCE(v.parse_status,'not_started')=?";args.add(filter);}
        String join=" FROM lawyer_case_file f LEFT JOIN lawyer_case_file_version v ON v.id=f.current_version_id AND v.tenant_id=f.tenant_id AND v.case_id=f.case_id";
        long total=jdbc.queryForObject("SELECT COUNT(*)"+join+where,Long.class,args.toArray());
        int limit=Math.max(1,Math.min(size,100)),offset=(Math.max(1,Math.min(page,100000))-1)*limit;
        var all=new ArrayList<>(args);all.add(limit);all.add(offset);
        var materials=jdbc.query("SELECT f.*"+join+where+" ORDER BY CASE COALESCE(v.parse_status,'not_started') WHEN 'failed' THEN 0 WHEN 'partial' THEN 1 WHEN 'running' THEN 2 WHEN 'queued' THEN 3 ELSE 4 END,f.created_at DESC,f.id DESC LIMIT ? OFFSET ?",mapping(CaseFile.class),all.toArray());
        return new PageResult<>(materials.stream().map(m->new MaterialRow(m,m.getCurrentVersionId()==null?null:version(caseId,m.getCurrentVersionId()))).toList(),total);
    }
    public Overview overview(Long caseId){
        access(caseId);var c=cases.owned(caseId);
        var rows=jdbc.queryForList("SELECT v.parse_status,v.parsed_pages,v.failed_pages,v.page_count FROM lawyer_case_file f LEFT JOIN lawyer_case_file_version v ON v.id=f.current_version_id AND v.tenant_id=f.tenant_id WHERE f.tenant_id=? AND f.case_id=? AND f.user_id=?",tenant(),caseId,actor());
        long success=0,partial=0,failed=0,parsed=0,pages=0,unknown=0,issues=0;
        for(var r:rows){String state=Objects.toString(r.get("parse_status"),"not_started");if(state.equals("success"))success++;if(state.equals("partial"))partial++;if(state.equals("failed"))failed++;
            parsed+=number(r.get("parsed_pages"));issues+=number(r.get("failed_pages"));if(r.get("page_count")==null){unknown++;issues++;}else pages+=number(r.get("page_count"));}
        return new Overview(c.getTitle(),rows.size(),success,partial,failed,parsed,pages,unknown,issues);
    }
    private static long number(Object value){return value instanceof Number n?n.longValue():0;}
    public List<CaseFileVersion> versions(Long caseId,Long materialId){access(caseId);material(caseId,materialId,false);return jdbc.query("SELECT * FROM lawyer_case_file_version WHERE material_id=? AND tenant_id=? AND case_id=? ORDER BY version_no DESC",mapping(CaseFileVersion.class),materialId,tenant(),caseId);}
    public List<CaseFilePage> pages(Long caseId,Long versionId){version(caseId,versionId);return jdbc.query("SELECT * FROM lawyer_case_file_page WHERE file_version_id=? AND tenant_id=? AND case_id=? ORDER BY page_index",mapping(CaseFilePage.class),versionId,tenant(),caseId);}
    public List<CaseParseLog> logs(Long caseId,Long versionId){version(caseId,versionId);return jdbc.query("SELECT * FROM lawyer_case_parse_log WHERE file_version_id=? AND tenant_id=? AND case_id=? ORDER BY create_time,id LIMIT 1000",mapping(CaseParseLog.class),versionId,tenant(),caseId);}
    public CaseParseJob job(Long caseId,Long id){access(caseId);return one("SELECT * FROM lawyer_case_parse_job WHERE id=? AND case_id=? AND tenant_id=? AND user_id=?",CaseParseJob.class,id,caseId,tenant(),actor());}
    public List<CaseParseJob> jobs(Long caseId){access(caseId);return jdbc.query("SELECT * FROM lawyer_case_parse_job WHERE case_id=? AND tenant_id=? AND user_id=? ORDER BY create_time DESC,id DESC LIMIT 50",mapping(CaseParseJob.class),caseId,tenant(),actor());}
    public CaseParseJob retry(Long caseId,Long versionId,String key,List<Integer> requested){
        access(caseId);key(key);
        return tx.execute(status->{cases.owned(caseId,true);version(caseId,versionId);return enqueue(caseId,versionId,key,requested==null?List.of():requested);});
    }
    private CaseParseJob enqueue(Long caseId,Long versionId,String key,List<Integer> requested){
        if(requested.size()>200||requested.stream().anyMatch(p->p==null||p<1||p>200)||new HashSet<>(requested).size()!=requested.size())throw new ServiceException("重试页码无效",400);
        var sorted=requested.stream().sorted().toList();String json;
        try{json=JSON.writeValueAsString(sorted);}catch(Exception e){throw new ServiceException("页码无效",400);}
        var existing=jdbc.query("SELECT * FROM lawyer_case_parse_job WHERE tenant_id=? AND case_id=? AND job_type='parse' AND idempotency_key=?",mapping(CaseParseJob.class),tenant(),caseId,key);
        if(!existing.isEmpty()){var j=existing.get(0);if(!j.getFileVersionId().equals(versionId)||!j.getRequestedPages().equals(json))throw new ServiceException("同一请求的重试范围已变化",409);return j;}
        var active=jdbc.query("SELECT * FROM lawyer_case_parse_job WHERE file_version_id=? AND state IN ('queued','running')",mapping(CaseParseJob.class),versionId);if(!active.isEmpty())return active.get(0);
        var version=version(caseId,versionId);
        if(!sorted.isEmpty()&&(version.getPageCount()==null||sorted.stream().anyMatch(p->p>version.getPageCount())))throw new ServiceException("页码超出当前文件范围",400);
        var success=jdbc.queryForList("SELECT page_index FROM lawyer_case_file_page WHERE file_version_id=? AND extract_status='success'",Integer.class,versionId);
        if(sorted.stream().anyMatch(success::contains))throw new ServiceException("此入口只重试失败页，不覆盖已成功或人工确认页",409);
        long id=id();jdbc.update("INSERT INTO lawyer_case_parse_job(id,tenant_id,case_id,user_id,file_version_id,idempotency_key,requested_pages,create_time,update_time) VALUES(?,?,?,?,?,?,?,UTC_TIMESTAMP(),UTC_TIMESTAMP())",id,tenant(),caseId,actor(),versionId,key,json);
        jdbc.update("UPDATE lawyer_case_file_version SET last_job_id=?,parse_status='queued',update_time=UTC_TIMESTAMP() WHERE id=?",id,versionId);
        log(caseId,versionId,id,null,"parse_queued",version.getParseStatus(),"queued",null,actor());return job(caseId,id);
    }
    public void cancel(Long caseId,Long jobId){access(caseId);tx.executeWithoutResult(status->{cases.owned(caseId,true);var j=job(caseId,jobId);if(!Set.of("queued","running").contains(j.getState()))return;
        jdbc.update("UPDATE lawyer_case_parse_job SET state='cancelled',lease_token=NULL,lease_until=NULL,error_code='USER_CANCELLED',error_message='任务已取消；已读取页保持可用',update_time=UTC_TIMESTAMP() WHERE id=?",jobId);
        if(!"parse".equals(j.getJobType()))return;
        jdbc.update("UPDATE lawyer_case_file_page SET extract_status='cancelled',error_code='USER_CANCELLED',error_message='此页尚未完成，可仅重试本页',version=version+1,update_time=UTC_TIMESTAMP() WHERE file_version_id=? AND extract_status IN ('queued','running')",j.getFileVersionId());
        aggregate(j.getFileVersionId(),"cancelled");log(caseId,j.getFileVersionId(),jobId,null,"parse_cancelled",j.getState(),"cancelled","USER_CANCELLED",actor());});}
    public CaseFileVersion adopt(Long caseId,Long materialId,String key){access(caseId);key(key);return tx.execute(status->{
        cases.owned(caseId,true);var m=material(caseId,materialId,true);if(m.getCurrentVersionId()!=null)return version(caseId,m.getCurrentVersionId());
        byte[] bytes;try{bytes=oss.readFileBytes(m.getOssId(),DossierFiles.MAX_BYTES);}catch(Exception e){throw new ServiceException("原件暂不可读取，请稍后重试或重新上传",503);}
        String name=Objects.toString(m.getDisplayName(),"材料.pdf");String mime=DossierFiles.validate(name,bytes);long versionId=id();
        jdbc.update("INSERT INTO lawyer_case_file_version(id,tenant_id,case_id,user_id,material_id,oss_id,version_no,file_name,sha256,file_bytes,mime,parser_version,create_by,create_time,update_time) VALUES(?,?,?,?,?,?,1,?,?,?,?,?,?,UTC_TIMESTAMP(),UTC_TIMESTAMP())",versionId,tenant(),caseId,actor(),materialId,m.getOssId(),name,DossierFiles.hash(bytes),bytes.length,mime,DossierFileReader.VERSION,actor());
        jdbc.update("UPDATE lawyer_case_file SET current_version_id=? WHERE id=?",versionId,materialId);enqueue(caseId,versionId,key,List.of());return version(caseId,versionId);
    });}
    public void confirmBlank(Long caseId,Long versionId,int page,int expectedVersion){access(caseId);tx.executeWithoutResult(status->{cases.owned(caseId,true);version(caseId,versionId);
        Integer running=jdbc.queryForObject("SELECT COUNT(*) FROM lawyer_case_parse_job WHERE file_version_id=? AND state IN ('queued','running')",Integer.class,versionId);if(running!=null&&running>0)throw new ServiceException("解析中不能确认空白页，请等待或取消任务",409);
        if(jdbc.update("UPDATE lawyer_case_file_page SET extract_status='success',ocr_status='not_needed',extraction_method='manual_blank',text_content='',char_count=0,empty_page_confirmed=1,error_code=NULL,error_message=NULL,version=version+1,update_time=UTC_TIMESTAMP() WHERE file_version_id=? AND page_index=? AND version=? AND tenant_id=? AND case_id=? AND extract_status<>'success'",versionId,page,expectedVersion,tenant(),caseId)!=1)throw new ServiceException("页面已变化，请刷新后重新核对",409);
        log(caseId,versionId,null,page,"manual_blank","failed","success",null,actor());aggregate(versionId,null);
    });}
    public byte[] preview(Long caseId,Long versionId,int page){var v=version(caseId,versionId);if(v.getPageCount()==null||page<1||page>v.getPageCount())throw new ServiceException("此物理页暂不可预览，可下载原件人工查看",422);
        try(var source=reader.open(checkedBytes(v),v.getMime())){return source.preview(page);}catch(Exception e){throw new ServiceException("原页预览失败，仍可下载原件；请稍后重试",503);}}
    public byte[] checkedBytes(CaseFileVersion v){
        Integer allowed=jdbc.queryForObject("SELECT COUNT(*) FROM lawyer_case c JOIN sys_oss o ON o.oss_id=? AND o.tenant_id=c.tenant_id AND o.create_by=? WHERE c.id=? AND c.tenant_id=? AND c.user_id=? AND c.deleted=0",Integer.class,v.getOssId(),v.getUserId(),v.getCaseId(),v.getTenantId(),v.getUserId());
        if(allowed==null||allowed!=1)throw new DossierReadException("ACCESS_REVOKED","案件或原件权限已失效");
        return org.ruoyi.common.tenant.helper.TenantHelper.threadScope(v.getTenantId(),()->{try{byte[] bytes=oss.readFileBytes(v.getOssId(),DossierFiles.MAX_BYTES);if(!DossierFiles.hash(bytes).equals(v.getSha256()))throw new DossierReadException("HASH_MISMATCH","原件指纹不一致，请核查存储版本");return bytes;}catch(java.io.IOException e){throw new DossierReadException("STORAGE_UNAVAILABLE","原件存储暂不可用，请稍后重试");}});
    }
    public void download(Long caseId,Long versionId,jakarta.servlet.http.HttpServletResponse response)throws java.io.IOException{
        var v=version(caseId,versionId);org.ruoyi.common.core.utils.file.FileUtils.setAttachmentResponseHeader(response,v.getFileName());response.setContentType(v.getMime());response.setHeader("X-Content-Type-Options","nosniff");response.getOutputStream().write(checkedBytes(v));
    }
    public Sources readable(Long caseId,Long rootOssId){access(caseId);var rows=jdbc.query("SELECT v.* FROM lawyer_case_file f JOIN lawyer_case_file_version v ON v.id=f.current_version_id WHERE f.tenant_id=? AND f.case_id=? AND f.user_id=? AND f.oss_id=?",mapping(CaseFileVersion.class),tenant(),caseId,actor(),rootOssId);
        if(rows.isEmpty())throw new ServiceException("请先在材料溯源工作台完成逐页解析",409);var v=rows.get(0);var pages=pages(caseId,v.getId()).stream().filter(p->"success".equals(p.getExtractStatus())).toList();return new Sources(v.getId(),v.getFileName(),v.getPageCount(),pages.size(),pages,v.getParseStatus());
    }
    public void log(Long caseId,Long versionId,Long job,Integer page,String action,String before,String after,String error,Long user){
        String t=jdbc.queryForObject("SELECT tenant_id FROM lawyer_case_file_version WHERE id=? AND case_id=?",String.class,versionId,caseId);
        jdbc.update("INSERT INTO lawyer_case_parse_log(id,tenant_id,case_id,file_version_id,job_id,page_index,action,before_status,after_status,error_code,operator_id,create_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP())",id(),t,caseId,versionId,job,page,action,before,after,error,user);
    }
    public void aggregate(Long versionId,String terminal){
        var v=one("SELECT * FROM lawyer_case_file_version WHERE id=? FOR UPDATE",CaseFileVersion.class,versionId);
        int good=jdbc.queryForObject("SELECT COUNT(*) FROM lawyer_case_file_page WHERE file_version_id=? AND extract_status='success'",Integer.class,versionId);
        int bad=v.getPageCount()==null?0:Math.max(0,v.getPageCount()-good);
        String state=v.getPageCount()!=null&&bad==0?"success":good>0?"partial":terminal!=null&&terminal.equals("cancelled")?"cancelled":"failed";
        jdbc.update("UPDATE lawyer_case_file_version SET parsed_pages=?,failed_pages=?,parse_status=?,error_code=?,error_message=?,update_time=UTC_TIMESTAMP() WHERE id=?",good,bad,state,state.equals("success")?null:state.equals("partial")?"UNREAD_PAGES":v.getErrorCode(),state.equals("success")?null:state.equals("partial")?"部分物理页尚未读取；下游仅使用成功页":v.getErrorMessage(),versionId);
    }
    private record BytesFile(String filename,String mime,byte[] bytes) implements MultipartFile{
        public String getName(){return "file";}public String getOriginalFilename(){return filename;}public String getContentType(){return mime;}public boolean isEmpty(){return bytes.length==0;}public long getSize(){return bytes.length;}public byte[] getBytes(){return bytes;}public java.io.InputStream getInputStream(){return new java.io.ByteArrayInputStream(bytes);}public void transferTo(java.io.File f)throws java.io.IOException{throw new java.io.IOException("原件不覆盖临时文件");}
    }
}
