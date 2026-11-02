package org.ruoyi.service.legal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

/** Private immutable template versions. Files are never exposed by a public storage URL. */
@Service
public class TemplateFiles {
    private static final ObjectMapper JSON=new ObjectMapper();
    private final JdbcTemplate jdbc;private final DocumentConversion conversion;
    public TemplateFiles(JdbcTemplate jdbc,DocumentConversion conversion){this.jdbc=jdbc;this.conversion=conversion;}
    public record Version(String id,Long templateId,String filename,String format,byte[] original,byte[] docx,List<TemplateLayout.Block> blocks){}
    public record Upload(String name,String documentType,String content,String sourceVersion,String sourceFormat,List<TemplateLayout.Block> blocks,String sourceFilename,String parseStatus,String analysisWarning){
        public Upload(String name,String documentType,String content,String sourceVersion,String sourceFormat,List<TemplateLayout.Block> blocks){this(name,documentType,content,sourceVersion,sourceFormat,blocks,"","rules","");}
    }
    public static String parseStatus(Version v){var state=TemplateLayout.state(v.docx());return state==null?(v.templateId()==null?"rules":"confirmed"):state.raw()?"uploaded":state.confirmed()?"confirmed":"AI".equals(state.method())?"parsed":"rules";}
    public static boolean usable(Version v){return "confirmed".equals(parseStatus(v));}
    private Upload result(Version v,String type){var state=TemplateLayout.state(v.docx());return new Upload(v.filename().replaceFirst("\\.[^.]+$", ""),type,v.blocks().stream().map(TemplateLayout.Block::text).reduce((a,b)->a+"\n"+b).orElse(""),v.id(),v.format(),v.blocks(),v.filename(),parseStatus(v),state==null?"":state.warning());}
    private String tenant(){String tenant=LoginHelper.getTenantId();if(tenant==null||LoginHelper.getUserId()==null)throw new DraftingException("请先登录",401);return tenant;}
    public Upload stage(MultipartFile file,String type){return stage(file,type,true);}
    public Upload stage(MultipartFile file,String type,boolean parse){
        String filename=Objects.toString(file.getOriginalFilename(),"").replace('\\','/');filename=filename.substring(filename.lastIndexOf('/')+1);
        String extension=filename.substring(filename.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        if(file.isEmpty()||file.getSize()>10*1024*1024||!Set.of("docx","pdf","txt","md","doc","rtf","odt").contains(extension))throw new DraftingException("请选择不超过10MB的DOCX、PDF、DOC、RTF、ODT、TXT或Markdown模板",400);
        try {
            byte[] original=file.getBytes(), docx;
            String content;
            if(!parse){
                // Preserve source bytes without text extraction, conversion or model calls.
                byte[] container;try(var doc=new org.apache.poi.xwpf.usermodel.XWPFDocument();var out=new java.io.ByteArrayOutputStream()){doc.write(out);container=out.toByteArray();}
                container=TemplateLayout.storeState(container,new TemplateLayout.State(true,false,"",""));
                String id=UUID.randomUUID().toString();
                jdbc.update("INSERT INTO legal_doc_template_file(version_id,tenant_id,uploaded_by,filename,source_format,source_bytes,docx_bytes,blocks_json,snapshot_json) VALUES (?,?,?,?,?,?,?,?,?)",id,tenant(),LoginHelper.getUserId(),filename,extension,original,container,"[]",TemplateSnapshot.encode(List.of(),new TemplateLayout.State(true,false,"",""),null));
                return result(new Version(id,null,filename,extension,original,container,List.of()),type);
            }
            if(extension.equals("docx")){docx=original;content=DocTemplateFile.read(file,type).content();}
            else if(Set.of("pdf","doc","rtf","odt").contains(extension)){docx=conversion.convert(original,extension,"docx");content=TemplateLayout.inspect(docx).stream().map(TemplateLayout.Block::text).reduce("",(a,b)->a+"\n"+b).strip();}
            else {content=DocTemplateFile.read(file,type).content();docx=new DocumentWord().generateTemplate(content);}
            if(content.isBlank()||content.length()>40000)throw new DraftingException("模板正文须为1至40000字",400);
            List<TemplateLayout.Block> blocks=TemplateLayout.inspect(docx,true);String id=UUID.randomUUID().toString();
            content=blocks.stream().map(TemplateLayout.Block::text).reduce((a,b)->a+"\n"+b).orElse("");
            var state=new TemplateLayout.State(false,false,"RULES","规则候选待核对");
            byte[] storedDocx=TemplateLayout.storeState(TemplateLayout.storeFields(docx,blocks),state);
            jdbc.update("INSERT INTO legal_doc_template_file(version_id,tenant_id,uploaded_by,filename,source_format,source_bytes,docx_bytes,blocks_json,snapshot_json) VALUES (?,?,?,?,?,?,?,?,?)",id,tenant(),LoginHelper.getUserId(),filename,extension,original,storedDocx,legacyBlocks(blocks),TemplateSnapshot.encode(blocks,state,null));
            return new Upload(filename.replaceFirst("\\.[^.]+$", ""),type,content,id,extension,blocks,filename,"rules","规则候选待核对");
        }catch(DraftingException e){throw e;}catch(Exception e){throw new DraftingException("模板文件保存失败，请检查模板文件存储表及文件格式",500);}
    }
    public Version load(String id){
        if(id==null||!id.matches("[a-f0-9-]{36}"))throw new DraftingException("模板版本无效",400);
        for(int attempt=0;attempt<2;attempt++){
            var rows=jdbc.query("SELECT version_id,template_id,filename,source_format,source_bytes,docx_bytes,snapshot_json FROM legal_doc_template_file WHERE version_id=? AND tenant_id=?",(rs,i)->{
                Long template=(Long)rs.getObject("template_id");String snapshot=rs.getString("snapshot_json");
                return new Version(rs.getString("version_id"),template,rs.getString("filename"),rs.getString("source_format"),rs.getBytes("source_bytes"),rs.getBytes("docx_bytes"),snapshot==null?null:TemplateSnapshot.decode(snapshot,template).blocks());
            },id,tenant());
            if(rows.isEmpty())throw new DraftingException("模板原文件不存在或不可访问，请重新上传",404);
            var version=rows.get(0);if(version.blocks()!=null)return version;
            ensureSnapshot(id);
        }
        throw new DraftingException("模板版本已变化，请刷新后重试",409);
    }
    /** Legacy-only CAS backfill. Normal snapshot reads never open a Word package or write data. */
    void ensureSnapshot(String id){
        String scope=tenant();
        for(int attempt=0;attempt<2;attempt++){
            var rows=jdbc.query("SELECT template_id,docx_bytes,blocks_json,snapshot_json FROM legal_doc_template_file WHERE version_id=? AND tenant_id=?",(rs,i)->new Legacy((Long)rs.getObject("template_id"),rs.getBytes("docx_bytes"),rs.getString("blocks_json"),rs.getString("snapshot_json")),id,scope);
            if(rows.isEmpty())throw new DraftingException("模板原文件不存在或不可访问，请重新上传",404);
            var row=rows.get(0);if(row.snapshot()!=null){TemplateSnapshot.decode(row.snapshot(),row.template());return;}
            try{
                var fallback=JSON.readValue(row.blocks(),new TypeReference<List<TemplateLayout.Block>>(){});
                var blocks=TemplateLayout.storedFields(row.docx(),fallback);
                String snapshot=TemplateSnapshot.encode(blocks,TemplateStateReader.read(row.docx()),row.template());
                int written=jdbc.update("UPDATE legal_doc_template_file SET snapshot_json=? WHERE version_id=? AND tenant_id=? AND snapshot_json IS NULL AND docx_bytes=? AND blocks_json=? AND template_id <=> ?",snapshot,id,scope,row.docx(),row.blocks(),row.template());
                if(written==1)return;
            }catch(DraftingException e){throw e;}catch(Exception e){throw new DraftingException("模板配置迁移失败，请联系管理员修复版本数据",422);}
        }
        throw new DraftingException("模板版本已变化，请刷新后重试",409);
    }
    private record Legacy(Long template,byte[] docx,String blocks,String snapshot){}
    public record Configuration(String version,String filename,String format,String parseStatus,String warning,boolean fieldsConfirmed,List<TemplateLayout.Block> blocks){}
    /** Configuration-only read used by both basic editing and field editing. No BLOB columns. */
    public Configuration currentConfiguration(Long template){
        String scope=tenant();
        for(int attempt=0;attempt<2;attempt++){
            var rows=jdbc.query("SELECT version_id,template_id,filename,source_format,snapshot_json FROM legal_doc_template_file WHERE template_id=? AND tenant_id=? ORDER BY bound_at DESC,version_id DESC LIMIT 1",(rs,i)->{
                String value=rs.getString("snapshot_json");
                var data=value==null?null:TemplateSnapshot.decode(value,(Long)rs.getObject("template_id"));
                return new Configuration(rs.getString("version_id"),rs.getString("filename"),rs.getString("source_format"),data==null?null:data.parseStatus(),data==null?null:data.warning(),data!=null&&data.fieldsConfirmed(),data==null?null:data.blocks());
            },template,scope);
            if(rows.isEmpty())return null;
            var config=rows.get(0);if(config.blocks()!=null)return config;
            ensureSnapshot(config.version());
        }
        throw new DraftingException("模板版本已变化，请刷新后重试",409);
    }
    void markAnalysisReviewed(Long template,String revision){
        if(revision!=null)jdbc.update("UPDATE legal_doc_template_field_task SET state='reviewed',phase='字段已核对保存',updated_at=CURRENT_TIMESTAMP(6) WHERE tenant_id=? AND template_id=? AND expected_revision=? AND state IN ('ready','partial')",tenant(),template,revision);
    }
    Configuration analysisConfiguration(String id){
        var configs=jdbc.query("SELECT template_id,version_id,filename,source_format,snapshot_json FROM legal_doc_template_file WHERE version_id=? AND tenant_id=?",(rs,i)->{
            var data=TemplateSnapshot.decode(rs.getString("snapshot_json"),(Long)rs.getObject("template_id"));
            return new Configuration(rs.getString("version_id"),rs.getString("filename"),rs.getString("source_format"),data.parseStatus(),data.warning(),data.fieldsConfirmed(),data.blocks());
        },id,tenant());
        if(configs.isEmpty())throw new DraftingException("解析结果不存在或不可访问",404);return configs.get(0);
    }
    String analysisWorkingCopy(String id){
        Long owner=jdbc.queryForObject("SELECT uploaded_by FROM legal_doc_template_file WHERE version_id=? AND tenant_id=?",Long.class,id,tenant());
        if(Objects.equals(owner,LoginHelper.getUserId()))return id;
        var rows=jdbc.query("SELECT filename,source_format,source_bytes,docx_bytes,blocks_json,snapshot_json FROM legal_doc_template_file WHERE version_id=? AND tenant_id=? AND template_id IS NULL",(rs,i)->new Object[]{rs.getString("filename"),rs.getString("source_format"),rs.getBytes("source_bytes"),rs.getBytes("docx_bytes"),rs.getString("blocks_json"),rs.getString("snapshot_json")},id,tenant());
        if(rows.isEmpty())throw new DraftingException("解析结果已变化，请刷新后重试",409);
        String next=UUID.randomUUID().toString();var data=rows.get(0);
        jdbc.update("INSERT INTO legal_doc_template_file(version_id,tenant_id,uploaded_by,filename,source_format,source_bytes,docx_bytes,blocks_json,snapshot_json) VALUES (?,?,?,?,?,?,?,?,?)",next,tenant(),LoginHelper.getUserId(),data[0],data[1],data[2],data[3],data[4],data[5]);
        return next;
    }
    private static String legacyBlocks(List<TemplateLayout.Block> blocks)throws Exception{return JSON.writeValueAsString(blocks.stream().map(b->new TemplateLayout.Block(b.id(),b.text(),b.editable())).toList());}
    public String currentVersionId(Long template){
        var ids=jdbc.queryForList("SELECT version_id FROM legal_doc_template_file WHERE template_id=? AND tenant_id=? ORDER BY bound_at DESC,version_id DESC LIMIT 1",String.class,template,tenant());
        return ids.isEmpty()?null:ids.get(0);
    }
    /** List-only SQL projection: no original bytes, DOCX bytes or full field snapshot payload. */
    public record SourceSummary(String version,String filename,String format,String parseStatus,String warning,boolean fieldsConfirmed){}
    public Map<Long,SourceSummary> currentSummaries(Collection<Long> templates){
        String scope=tenant();var ids=templates.stream().filter(Objects::nonNull).distinct().toList();
        if(ids.isEmpty())return Map.of();
        String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
        String sql="SELECT f.template_id,f.version_id,f.filename,f.source_format,"
            +"JSON_UNQUOTE(JSON_EXTRACT(f.snapshot_json,'$.schemaVersion')) AS snapshot_schema,"
            +"JSON_UNQUOTE(JSON_EXTRACT(f.snapshot_json,'$.parseStatus')) AS parse_status,"
            +"CASE WHEN JSON_TYPE(JSON_EXTRACT(f.snapshot_json,'$.warning'))='NULL' THEN NULL ELSE JSON_UNQUOTE(JSON_EXTRACT(f.snapshot_json,'$.warning')) END AS analysis_warning,"
            +"JSON_UNQUOTE(JSON_EXTRACT(f.snapshot_json,'$.fieldsConfirmed')) AS fields_confirmed "
            +"FROM legal_doc_template_file f WHERE f.tenant_id=? AND f.template_id IN ("+placeholders+") AND f.version_id=("
            +"SELECT latest.version_id FROM legal_doc_template_file latest WHERE latest.tenant_id=f.tenant_id AND latest.template_id=f.template_id "
            +"ORDER BY latest.bound_at DESC,latest.version_id DESC LIMIT 1)";
        var args=new ArrayList<Object>();args.add(scope);args.addAll(ids);
        for(int attempt=0;attempt<2;attempt++){
            var result=new HashMap<Long,SourceSummary>();var missing=new ArrayList<String>();
            jdbc.query(sql,(org.springframework.jdbc.core.RowCallbackHandler)rs->{
                String schema=rs.getString("snapshot_schema"),version=rs.getString("version_id");
                if(schema==null){missing.add(version);return;}
                String status=rs.getString("parse_status");boolean confirmed="true".equals(rs.getString("fields_confirmed"));
                if(!"1".equals(schema)||!Set.of("uploaded","rules","parsed","confirmed").contains(Objects.toString(status,""))||confirmed!="confirmed".equals(status))throw new DraftingException("模板配置快照损坏，请联系管理员修复版本数据",422);
                result.put(rs.getLong("template_id"),new SourceSummary(version,rs.getString("filename"),rs.getString("source_format"),status,rs.getString("analysis_warning"),confirmed));
            },args.toArray());
            if(missing.isEmpty())return result;
            for(var version:missing)ensureSnapshot(version);
        }
        throw new DraftingException("模板版本已变化，请刷新后重试",409);
    }
    public Version current(Long template){String id=currentVersionId(template);return id==null?null:load(id);}
    public boolean hasSource(Long template){
        if(template==null)return false;var summary=currentSummaries(List.of(template)).get(template);return summary!=null&&summary.fieldsConfirmed();
    }
    public void bind(String id,Long template,List<String> editable,String content,boolean confirmed){
        bind(id,template,editable,content,confirmed,null,false);
    }
    public void bind(String id,Long template,List<String> editable,String content,boolean confirmed,List<TemplateFields.Field> fields,boolean fieldsConfirmed){
        bind(id,template,editable,content,confirmed,fields,fieldsConfirmed,false);
    }
    public void bind(String id,Long template,List<String> editable,String content,boolean confirmed,List<TemplateFields.Field> fields,boolean fieldsConfirmed,boolean draft){
        Version v=load(id);
        if(v.templateId()!=null){
            if(!v.templateId().equals(template))throw new DraftingException("模板原文件已被其他模板使用",409);
            if(!Objects.equals(current(template).id(),id))throw new DraftingException("模板版本已变化，请刷新后重试",409);
            var selected=v.blocks().stream().filter(TemplateLayout.Block::editable).map(TemplateLayout.Block::id).collect(java.util.stream.Collectors.toSet());
            var existing=v.blocks().stream().flatMap(b->b.fields()==null?java.util.stream.Stream.empty():b.fields().stream()).toList();
            if((!fieldsConfirmed||usable(v))&&(fields==null||fields.equals(existing))&&(editable==null||selected.equals(new HashSet<>(editable))))return;
            v=cloneVersion(v);id=v.id();
        }
        requireOwner(v);
        var state=TemplateLayout.state(v.docx());
        if(state!=null&&state.raw()){
            if(!draft)throw new DraftingException("原文件尚未解析，请先解析并核对填写字段",400);
            bindVersion(v,template,List.of(),TemplateLayout.storeState(v.docx(),new TemplateLayout.State(true,false,"","")));return;
        }
        // Field review is the single confirmation. Legacy clients may still send
        // their old layout flag; no client is required to request a PDF preview.
        if(!draft&&!fieldsConfirmed&&!confirmed)throw new DraftingException("请核对填写字段或段落，并确认后保存",400);
        
        Set<String> keys=new HashSet<>(editable==null?List.of():editable);
        if((keys.isEmpty()&&!draft)||!v.blocks().stream().map(TemplateLayout.Block::id).collect(java.util.stream.Collectors.toSet()).containsAll(keys))throw new DraftingException("请确认模板中允许起草填写的段落",400);
        if(v.blocks().stream().anyMatch(b->b.text().contains("{{")&&!keys.contains(b.id())))throw new DraftingException("含模板占位符的段落必须设为填写区域",400);
        List<TemplateFields.Field> configured=null;
        if(v.blocks().stream().anyMatch(b->b.fields()!=null)){
            if(!draft&&!fieldsConfirmed)throw new DraftingException("请核对填写字段的含义、位置及单位后保存",400);
            configured=TemplateFields.validate(v.blocks(),fields);
            if(!draft&&configured.stream().noneMatch(f->f.enabled()&&keys.contains(f.blockId())))throw new DraftingException("未配置可填写字段，请核对原模板的空白位置",400);
        }else TemplateLayout.validateEditable(v.docx(),keys);
        String extracted=v.blocks().stream().map(TemplateLayout.Block::text).reduce("",(a,b)->a+"\n"+b).replaceAll("\\s","");
        if(!extracted.equals(content.replaceAll("[#*\\s]","")) && !extracted.replaceAll("[#*]","").equals(content.replaceAll("[#*\\s]","")))throw new DraftingException("请通过替换文件修改版式模板正文",400);
        final var mappings=configured;
        var blocks=v.blocks().stream().map(b->new TemplateLayout.Block(b.id(),b.text(),keys.contains(b.id()),mappings==null?null:mappings.stream().filter(f->f.blockId().equals(b.id())).toList())).toList();
        var previous=TemplateLayout.state(v.docx());
        bindVersion(v,template,blocks,TemplateLayout.storeState(TemplateLayout.storeFields(v.docx(),blocks),new TemplateLayout.State(false,!draft,previous==null?"RULES":previous.method(),previous==null?"":previous.warning())));
    }
    private void bindVersion(Version v,Long template,List<TemplateLayout.Block> blocks,byte[] docx){
        try{if(jdbc.update("UPDATE legal_doc_template_file SET template_id=?,blocks_json=?,docx_bytes=?,snapshot_json=?,bound_at=CURRENT_TIMESTAMP(6) WHERE version_id=? AND tenant_id=? AND template_id IS NULL",template,legacyBlocks(blocks),docx,TemplateSnapshot.encode(blocks,TemplateStateReader.read(docx),template),v.id(),tenant())!=1)throw new DraftingException("模板版本已变化，请重试",409);}
        catch(DraftingException e){throw e;}catch(Exception e){throw new DraftingException("模板版本保存失败",500);}
    }
    private void requireOwner(Version v){if(!Objects.equals(jdbc.queryForObject("SELECT uploaded_by FROM legal_doc_template_file WHERE version_id=? AND tenant_id=?",Long.class,v.id(),tenant()),LoginHelper.getUserId()))throw new DraftingException("上传文件不可访问",404);}
    private Version cloneVersion(Version v){
        try{String id=UUID.randomUUID().toString();jdbc.update("INSERT INTO legal_doc_template_file(version_id,tenant_id,uploaded_by,filename,source_format,source_bytes,docx_bytes,blocks_json,snapshot_json) VALUES (?,?,?,?,?,?,?,?,?)",id,tenant(),LoginHelper.getUserId(),v.filename(),v.format(),v.original(),v.docx(),legacyBlocks(v.blocks()),TemplateSnapshot.encode(v.blocks(),TemplateStateReader.read(v.docx()),null));return new Version(id,null,v.filename(),v.format(),v.original(),v.docx(),v.blocks());}
        catch(Exception e){throw new DraftingException("模板工作版本保存失败",500);}
    }
    public byte[] preview(String id){Version version=load(id);if("uploaded".equals(parseStatus(version)))throw new DraftingException("原文件尚未解析，请先解析字段后预览",400);return conversion.convert(version.docx(),"docx","pdf");}
    public Upload reidentify(Long template,String type){Version v=current(template);if(v==null)throw new DraftingException("该模板没有原文件，请先补传原文件",422);return parse(v.id(),type);}
    public Upload reidentify(Long template){return reidentify(template,"");}
    public Upload parse(String id,String type){
        Version v=load(id);if(v.templateId()==null)requireOwner(v);
        if(v.templateId()!=null&&!Objects.equals(current(v.templateId()).id(),v.id()))throw new DraftingException("模板版本已变化，请刷新后重试",409);
        // Always create a working version; saved configuration remains intact until confirmation.
        return stage(new MultipartFile(){
            public String getName(){return "file";}public String getOriginalFilename(){return v.filename();}public String getContentType(){return "application/octet-stream";}public boolean isEmpty(){return v.original().length==0;}public long getSize(){return v.original().length;}public byte[] getBytes(){return v.original();}public java.io.InputStream getInputStream(){return new java.io.ByteArrayInputStream(v.original());}public void transferTo(java.io.File dest)throws java.io.IOException{java.nio.file.Files.write(dest.toPath(),v.original());}
        },type,true);
    }
    public void requireAnalysisVersion(String id){var v=load(id);requireOwner(v);if(v.templateId()!=null||"uploaded".equals(parseStatus(v)))throw new DraftingException("请先创建解析工作版本，原有字段未修改",409);}
    public TemplateFieldAnalysis.Analysis saveAnalysis(String id,TemplateFieldAnalysis.Analysis result){
        var v=load(id);requireOwner(v);if(v.templateId()!=null||"uploaded".equals(parseStatus(v)))throw new DraftingException("请先创建解析工作版本，原有字段未修改",409);
        var fields=TemplateFields.validate(v.blocks(),result.fields());
        var blocks=v.blocks().stream().map(b->new TemplateLayout.Block(b.id(),b.text(),b.editable(),fields.stream().filter(f->f.blockId().equals(b.id())).toList())).toList();
        byte[] data=TemplateLayout.storeState(TemplateLayout.storeFields(v.docx(),blocks),new TemplateLayout.State(false,false,result.method(),result.warning()));
        if(jdbc.update("UPDATE legal_doc_template_file SET docx_bytes=?,snapshot_json=? WHERE version_id=? AND tenant_id=? AND uploaded_by=? AND template_id IS NULL",data,TemplateSnapshot.encode(blocks,new TemplateLayout.State(false,false,result.method(),result.warning()),null),id,tenant(),LoginHelper.getUserId())!=1)throw new DraftingException("模板版本已变化，请重试",409);
        return result;
    }
    public void validateRequest(String request){
        var expected=TemplateDrafting.expected(request);if(expected==null)return;Version v=load(expected.documentTemplate());
        if(!usable(v)||v.templateId()==null||!TemplateFields.logical(v.blocks()).equals(expected.blocks())||!Integer.valueOf(1).equals(jdbc.queryForObject("SELECT COUNT(*) FROM legal_doc_template WHERE id=? AND tenant_id=? AND status='0'",Integer.class,v.templateId(),tenant())))throw new DraftingException("模板版本不可用，请刷新模板后重试",422);
    }
    public DocumentConversion conversion(){return conversion;}
}
