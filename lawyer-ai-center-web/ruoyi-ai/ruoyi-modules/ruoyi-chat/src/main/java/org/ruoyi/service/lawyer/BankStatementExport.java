package org.ruoyi.service.lawyer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.service.OssService;
import org.ruoyi.domain.entity.lawyer.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service @RequiredArgsConstructor
public class BankStatementExport implements DossierJobHandler {
    private final BankStatementService bank;private final DossierMaterialService materials;private final CaseAccess cases;
    private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final OssService oss;
    private static final ObjectMapper JSON=new ObjectMapper();
    public record Request(BankStatementService.Filter filter,boolean includePending,boolean deduplicate,String format,String idempotencyKey){}
    public record Receipt(String id,String jobId,String snapshotHash,int rowCount,int pendingCount,String format){}
    public Receipt request(Long caseId,Request input){bank.access(caseId);if(input.format()==null||!Set.of("xlsx","csv").contains(input.format())||input.filter()==null||input.idempotencyKey()==null||!input.idempotencyKey().matches("[A-Za-z0-9_-]{12,64}"))throw new ServiceException("导出格式、范围或请求标识无效",400);
        String requestHash=DossierFiles.hash(BankStatementService.json(Map.of("filter",input.filter(),"includePending",input.includePending(),"deduplicate",input.deduplicate(),"format",input.format())).getBytes(StandardCharsets.UTF_8));
        return tx.execute(s->{cases.owned(caseId,true);var prior=jdbc.queryForList("SELECT e.* FROM lawyer_case_bank_export e JOIN lawyer_case_parse_job j ON j.id=e.job_id WHERE j.tenant_id=? AND j.case_id=? AND j.job_type='bank_export' AND j.idempotency_key=?",DossierMaterialService.tenant(),caseId,input.idempotencyKey());if(!prior.isEmpty()){var p=prior.get(0);if(!requestHash.equals(p.get("request_hash")))throw new ServiceException("同一导出请求的范围已变化",409);return receipt(p);}
            bank.list(caseId,input.filter());var selected=bank.filtered(caseId,input.filter()).stream().filter(r->input.includePending()||r.getIncluded()&&"confirmed".equals(r.getReviewStatus())).toList();if(selected.isEmpty())throw new ServiceException("当前范围没有可导出的流水；可选择显著标记的含待核版本",400);
            var sources=new ArrayList<Map<String,Object>>();var checked=new HashMap<Long,CaseFileVersion>();var pageCache=new HashMap<Long,CaseFilePage>();
            for(var r:selected){if(!checked.containsKey(r.getFileVersionId())){var v=materials.version(caseId,r.getFileVersionId());materials.checkedBytes(v);checked.put(v.getId(),v);for(var p:materials.pages(caseId,v.getId()))pageCache.put(p.getId(),p);}
                var p=pageCache.get(r.getPageId());var v=checked.get(r.getFileVersionId());String text=p==null?"":Objects.toString(p.getTextContent(),"");boolean valid=p!=null&&"success".equals(p.getExtractStatus())&&Objects.equals(p.getTextHash(),r.getPageHash())&&Objects.equals(p.getTextHash(),DossierFiles.hash(text.getBytes(StandardCharsets.UTF_8)))&&Objects.equals(v.getSha256(),r.getSourceHash())&&r.getFragmentStart()>=0&&r.getFragmentEnd()<=text.length()&&r.getFragmentEnd()>r.getFragmentStart()&&text.substring(r.getFragmentStart(),r.getFragmentEnd()).equals(r.getFragmentText());
                if(!valid&&"confirmed".equals(r.getReviewStatus()))throw new ServiceException("已核流水来源已变化，请重新复核后导出",409);
                var source=new LinkedHashMap<String,Object>();source.put("rowId",r.getId().toString());source.put("fileName",v.getFileName());source.put("fileVersionId",v.getId().toString());source.put("sourceHash",v.getSha256());source.put("page",r.getPageIndex());source.put("pageLabel",p==null?"来源页不可用":p.getPageLabel());source.put("tableRow",r.getTableRow());source.put("start",r.getFragmentStart());source.put("end",r.getFragmentEnd());source.put("pageHash",r.getPageHash());source.put("fragment",BankStatementParser.safeText(r.getFragmentText()));source.put("verified",valid);source.put("reviewStatus",r.getReviewStatus());source.put("included",r.getIncluded());sources.add(source);
            }
            var totals=bank.totals(caseId,selected);var selectedIds=new HashSet<String>();for(var row:selected)selectedIds.add(row.getId().toString());
            var duplicate=bank.duplicates(caseId).stream().filter(pair->selectedIds.contains(pair.get("leftRowId").toString())||selectedIds.contains(pair.get("rightRowId").toString())).map(pair->{var value=new LinkedHashMap<String,Object>(pair);value.put("leftInScope",selectedIds.contains(pair.get("leftRowId").toString()));value.put("rightInScope",selectedIds.contains(pair.get("rightRowId").toString()));return value;}).toList();
            var coverage=bank.unread(caseId).stream().filter(page->checked.containsKey(Long.valueOf(page.get("fileVersionId").toString()))).toList();
            var output=selected;
            if(input.deduplicate()){
                var parents=bank.parents(caseId);var undecided=bank.unresolved(caseId);var representatives=new LinkedHashMap<Long,CaseBankRow>();
                for(var row:selected){long group=BankStatementService.root(parents,row.getId());var previous=representatives.get(group);if(previous==null||!BankStatementService.eligibleForTotals(previous,undecided)&&BankStatementService.eligibleForTotals(row,undecided))representatives.put(group,row);}
                output=List.copyOf(representatives.values());
            }
            var snapshot=new LinkedHashMap<String,Object>();snapshot.put("schemaVersion",2);snapshot.put("scope",input.filter());snapshot.put("includePending",input.includePending());snapshot.put("deduplicate",input.deduplicate());snapshot.put("totals",totals);snapshot.put("rows",output);snapshot.put("sources",sources);snapshot.put("duplicates",duplicate);snapshot.put("unparsed",coverage);snapshot.put("createdAt",java.time.Instant.now().toString());snapshot.put("actorId",DossierMaterialService.actor().toString());snapshot.put("coverageScope","页级覆盖仅含本次导出交易涉及的文件版本；未解析行无法可靠归属交易日期，保留这些版本各页的缺口。重复组仅含涉及本次交易的组，组内范围外成员明确标记。");snapshot.put("notice","内部核对资料，非银行认证流水；金额为精确十进制文本。未核/过期不计已核合计；仅人工同一笔组去重，优先保留实际参与已核合计的代表行，所有所选原始来源及核验状态均保留。");
            String json=BankStatementService.json(snapshot);if(json.getBytes(StandardCharsets.UTF_8).length>12*1024*1024)throw new ServiceException("本次导出快照超过12MB，请缩小日期或文件范围",413);
            long id=DossierMaterialService.id(),job=DossierMaterialService.id();String hash=DossierFiles.hash(json.getBytes(StandardCharsets.UTF_8));
            var busy=jdbc.queryForList("SELECT id FROM lawyer_case_parse_job WHERE file_version_id=? AND state IN ('queued','running')",Long.class,selected.get(0).getFileVersionId());if(!busy.isEmpty())throw new ServiceException("此材料任务仍在运行，请完成后再导出固定快照",409);
            jdbc.update("INSERT INTO lawyer_case_parse_job(id,tenant_id,case_id,user_id,file_version_id,job_type,idempotency_key,requested_pages,create_time,update_time) VALUES(?,?,?,?,?,'bank_export',?,'[]',UTC_TIMESTAMP(),UTC_TIMESTAMP())",job,DossierMaterialService.tenant(),caseId,DossierMaterialService.actor(),selected.get(0).getFileVersionId(),input.idempotencyKey());
            int pending=(int)output.stream().filter(r->!"confirmed".equals(r.getReviewStatus())).count();jdbc.update("INSERT INTO lawyer_case_bank_export(id,tenant_id,case_id,user_id,job_id,format,request_hash,snapshot_json,snapshot_hash,row_count,pending_count,create_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,UTC_TIMESTAMP())",id,DossierMaterialService.tenant(),caseId,DossierMaterialService.actor(),job,input.format(),requestHash,json,hash,output.size(),pending);
            return new Receipt(Long.toString(id),Long.toString(job),hash,output.size(),pending,input.format());
        });
    }
    static Receipt receipt(Map<String,Object> r){return new Receipt(r.get("id").toString(),r.get("job_id").toString(),r.get("snapshot_hash").toString(),((Number)r.get("row_count")).intValue(),((Number)r.get("pending_count")).intValue(),r.get("format").toString());}
    @Override public boolean supports(String type){return "bank_export".equals(type);}
    @Override public void execute(CaseParseJob job,String lease){
        var rows=jdbc.queryForList("SELECT * FROM lawyer_case_bank_export WHERE job_id=? AND tenant_id=? AND case_id=? AND user_id=?",job.getId(),job.getTenantId(),job.getCaseId(),job.getUserId());if(rows.isEmpty())throw new DossierReadException("EXPORT_MISSING","导出快照不可用");var r=rows.get(0);String data=r.get("snapshot_json").toString();if(!DossierFiles.hash(data.getBytes(StandardCharsets.UTF_8)).equals(r.get("snapshot_hash")))throw new DossierReadException("SNAPSHOT_CHANGED","导出快照指纹不一致");
        byte[] bytes=render(data,r.get("format").toString());if(bytes.length>DossierFiles.MAX_BYTES)throw new DossierReadException("EXPORT_LIMIT","导出文件超过10MB，请缩小范围重新导出");
        Long saved=null;
        try{if(!Boolean.TRUE.equals(tx.execute(s->bank.fence(job,lease))))return;
            String format=r.get("format").toString(),name="流水核对_"+r.get("id")+"."+format;
            saved=org.ruoyi.common.tenant.helper.TenantHelper.threadScope(job.getTenantId(),()->oss.uploadFile(new ExportFile(name,bytes)).getOssId());Long object=saved;
            // The generic OSS filler uses -1 for background work. Attribute only this
            // newly created object to the persisted job actor, without a forged login.
            jdbc.update("UPDATE sys_oss SET create_by=? WHERE oss_id=? AND tenant_id=? AND (create_by=-1 OR create_by IS NULL)",job.getUserId(),object,job.getTenantId());
            if(jdbc.queryForObject("SELECT COUNT(*) FROM sys_oss WHERE oss_id=? AND tenant_id=? AND create_by=?",Integer.class,object,job.getTenantId(),job.getUserId())!=1)throw new DossierReadException("EXPORT_OWNER_MISMATCH","导出对象归属不一致，已停止发布");
            boolean stored=Boolean.TRUE.equals(tx.execute(s->{if(!bank.fence(job,lease))return false;jdbc.update("UPDATE lawyer_case_bank_export SET oss_id=?,file_hash=? WHERE id=?",object,DossierFiles.hash(bytes),r.get("id"));jdbc.update("UPDATE lawyer_case_parse_job SET state='success',progress_done=?,progress_total=?,lease_token=NULL,lease_until=NULL,update_time=UTC_TIMESTAMP() WHERE id=?",r.get("row_count"),r.get("row_count"),job.getId());return true;}));if(!stored){oss.deleteFile(saved);saved=null;}
        }catch(Exception e){if(saved!=null)try{oss.deleteFile(saved);}catch(Exception ignored){}if(e instanceof DossierReadException d)throw d;throw new DossierReadException("EXPORT_UNAVAILABLE","导出生成或存储失败，可使用同一请求重试；快照仍保留");}
    }
    public Map<String,Object> status(Long caseId,Long id){bank.access(caseId);var rows=jdbc.queryForList("SELECT e.id,e.job_id,e.snapshot_hash,e.row_count,e.pending_count,e.format,j.state,j.error_message FROM lawyer_case_bank_export e JOIN lawyer_case_parse_job j ON j.id=e.job_id WHERE e.id=? AND e.tenant_id=? AND e.case_id=? AND e.user_id=?",id,DossierMaterialService.tenant(),caseId,DossierMaterialService.actor());if(rows.isEmpty())throw new ServiceException("导出不可用",404);return BankStatementService.camel(rows.get(0));}
    public List<Map<String,Object>> list(Long caseId){bank.access(caseId);return jdbc.queryForList("SELECT e.id,e.job_id,e.snapshot_hash,e.row_count,e.pending_count,e.format,j.state,j.error_message FROM lawyer_case_bank_export e JOIN lawyer_case_parse_job j ON j.id=e.job_id WHERE e.tenant_id=? AND e.case_id=? AND e.user_id=? ORDER BY e.create_time DESC,e.id DESC LIMIT 20",DossierMaterialService.tenant(),caseId,DossierMaterialService.actor()).stream().map(BankStatementService::camel).toList();}
    public void retry(Long caseId,Long id){bank.access(caseId);tx.executeWithoutResult(s->{cases.owned(caseId,true);status(caseId,id);var jobs=jdbc.queryForList("SELECT j.id,j.file_version_id,j.state FROM lawyer_case_bank_export e JOIN lawyer_case_parse_job j ON j.id=e.job_id WHERE e.id=? FOR UPDATE",id);var j=jobs.get(0);if(!Set.of("failed","cancelled").contains(j.get("state")))return;if(!jdbc.queryForList("SELECT id FROM lawyer_case_parse_job WHERE file_version_id=? AND state IN ('queued','running')",j.get("file_version_id")).isEmpty())throw new ServiceException("材料仍有活动任务，请稍后恢复导出",409);jdbc.update("UPDATE lawyer_case_parse_job SET state='queued',error_code=NULL,error_message=NULL,lease_token=NULL,lease_until=NULL,update_time=UTC_TIMESTAMP() WHERE id=?",j.get("id"));});}
    public byte[] download(Long caseId,Long id){bank.access(caseId);var records=jdbc.queryForList("SELECT e.oss_id,e.file_hash,j.state FROM lawyer_case_bank_export e JOIN lawyer_case_parse_job j ON j.id=e.job_id WHERE e.id=? AND e.tenant_id=? AND e.case_id=? AND e.user_id=?",id,DossierMaterialService.tenant(),caseId,DossierMaterialService.actor());if(records.isEmpty())throw new ServiceException("导出不可用",404);var r=records.get(0);if(!"success".equals(r.get("state"))||r.get("oss_id")==null)throw new ServiceException("导出尚未就绪，请查询任务状态",409);if(jdbc.queryForObject("SELECT COUNT(*) FROM sys_oss WHERE oss_id=? AND tenant_id=? AND create_by=?",Integer.class,r.get("oss_id"),DossierMaterialService.tenant(),DossierMaterialService.actor())!=1)throw new ServiceException("导出不可用",404);try{byte[] bytes=oss.readFileBytes(((Number)r.get("oss_id")).longValue(),DossierFiles.MAX_BYTES);if(!DossierFiles.hash(bytes).equals(r.get("file_hash")))throw new IOException();return bytes;}catch(IOException e){throw new ServiceException("导出文件暂不可读取或指纹不符，请联系管理员恢复",503);}}
    @SuppressWarnings("unchecked") public static byte[] render(String data,String format){try{
        var snapshot=JSON.readValue(data,new TypeReference<Map<String,Object>>(){});var rows=(List<Map<String,Object>>)snapshot.get("rows");
        var table=new ArrayList<List<String>>();table.add(List.of("流水ID","交易时间原文","时间","方向","金额原文","金额(精确文本)","币种","余额原文","余额(精确文本)","对方账户(脱敏)","对方户名(脱敏)","交易号","摘要","文件版本","物理/系统页","核验状态","纳入范围","异常","未知原因"));
        var keys=List.of("id","tradeTimeRaw","tradeAt","direction","amountRaw","amount","currency","balanceRaw","balance","counterAccount","counterparty","transactionRef","summary","fileVersionId","pageIndex","reviewStatus","included","anomalyJson","reasonsJson");for(var row:rows)table.add(keys.stream().map(k->k.equals("summary")?BankStatementParser.safeText(Objects.toString(row.get(k),"")):Objects.toString(row.get(k),"")).toList());
        if(format.equals("csv")){var value=new StringBuilder("\uFEFF");for(var row:table)value.append(row.stream().map(BankStatementExport::csvCell).collect(java.util.stream.Collectors.joining(","))).append("\r\n");return value.toString().getBytes(StandardCharsets.UTF_8);}
        try(var book=new XSSFWorkbook();var out=new ByteArrayOutputStream()){
            sheet(book,"明细",table);
            var sources=new ArrayList<List<String>>();sources.add(List.of("流水ID","文件名","版本ID","原件SHA256","页","页标签","行","片段起点","终点","页文本SHA256","片段(脱敏)","来源校验","来源交易核验状态","来源交易纳入范围"));for(var source:(List<Map<String,Object>>)snapshot.get("sources"))sources.add(List.of("rowId","fileName","fileVersionId","sourceHash","page","pageLabel","tableRow","start","end","pageHash","fragment","verified","reviewStatus","included").stream().map(k->k.equals("fragment")?BankStatementParser.safeText(Objects.toString(source.get(k),"")):Objects.toString(source.get(k),Set.of("reviewStatus","included").contains(k)?"未记录（历史快照）":"")).toList());sheet(book,"来源",sources);
            var anomalies=new ArrayList<List<String>>();anomalies.add(List.of("流水/页","类型","说明"));for(var row:rows){for(String key:List.of("anomalyJson","reasonsJson"))if(!"[]".equals(row.get(key)))anomalies.add(List.of(row.get("id").toString(),key,Objects.toString(row.get(key),"")));}for(Object unread:(List<?>)snapshot.get("unparsed"))anomalies.add(List.of("页级读取/版式","unparsed",BankStatementService.json(coverageForExport((Map<String,Object>)unread))));for(Object pair:(List<?>)snapshot.get("duplicates"))anomalies.add(List.of("重复判定","duplicate",BankStatementService.json(pair)));sheet(book,"异常",anomalies);
            var notes=new ArrayList<List<String>>();notes.add(List.of("导出说明","固定快照"));for(String k:List.of("notice","createdAt","actorId","includePending","deduplicate","totals","scope","coverageScope"))notes.add(List.of(k,BankStatementService.json(snapshot.get(k))));notes.add(List.of("状态","本文件含待核/过期条目="+snapshot.get("includePending")+"；各行核验状态不可省略。"));sheet(book,"导出说明",notes);book.write(out);return out.toByteArray();}
    }catch(Exception e){throw new DossierReadException("EXPORT_RENDER_FAILED","导出快照结构或文件渲染失败，可重试同一快照");}}
    private static Map<String,Object> coverageForExport(Map<String,Object> page){
        // Version IDs and page/offset coordinates are provenance, not sensitive content.
        // Apply the content masker before serialization so those identifiers remain usable.
        var safe=new LinkedHashMap<String,Object>(page);
        for(String key:List.of("unparsedJson","warningsJson"))if(page.get(key)!=null)safe.put(key,BankStatementParser.safeText(page.get(key).toString()));
        if(page.get("lines") instanceof List<?> lines){
            safe.put("lines",lines.stream().map(item->{
                if(!(item instanceof Map<?,?> line))throw new IllegalArgumentException("Invalid coverage line");
                var value=new LinkedHashMap<String,Object>();
                line.forEach((key,text)->value.put(key.toString(),key.toString().equals("text")?BankStatementParser.safeText(Objects.toString(text,"")):text));
                return value;
            }).toList());
        }
        return safe;
    }
    static void sheet(XSSFWorkbook book,String name,List<List<String>> data){var sheet=book.createSheet(name);sheet.createFreezePane(0,1);int index=0;for(var values:data){var row=sheet.createRow(index++);for(int c=0;c<values.size();c++){String value=values.get(c);row.createCell(c).setCellValue(value.length()>32767?value.substring(0,32740)+"…（完整原文见原件）":value);}}if(!data.isEmpty()){sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,Math.max(0,data.size()-1),0,data.get(0).size()-1));for(int c=0;c<data.get(0).size();c++)sheet.setColumnWidth(c,Math.min(80*256,c==0?24*256:30*256));}}
    public static String csvCell(String value){String text=value==null?"":value,leading=text.stripLeading();while(!leading.isEmpty()&&(Character.getType(leading.charAt(0))==Character.FORMAT||Character.isSpaceChar(leading.charAt(0))))leading=leading.substring(1).stripLeading();if(!leading.isEmpty()&&"=+@-".indexOf(leading.charAt(0))>=0)text="'"+text;return "\""+text.replace("\"","\"\"")+"\"";}
    private record ExportFile(String name,byte[] bytes) implements MultipartFile {public String getName(){return "file";}public String getOriginalFilename(){return name;}public String getContentType(){return name.endsWith("xlsx")?"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":"text/csv";}public boolean isEmpty(){return bytes.length==0;}public long getSize(){return bytes.length;}public byte[] getBytes(){return bytes;}public InputStream getInputStream(){return new ByteArrayInputStream(bytes);}public void transferTo(File file)throws IOException{throw new IOException("Use the immutable export stream");}}
}
