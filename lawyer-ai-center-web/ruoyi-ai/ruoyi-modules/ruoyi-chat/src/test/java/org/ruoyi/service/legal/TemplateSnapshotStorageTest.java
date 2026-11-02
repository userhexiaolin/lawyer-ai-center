package org.ruoyi.service.legal;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("dev")
@EnabledIfSystemProperty(named="template.snapshot.mysql",matches="true")
class TemplateSnapshotStorageTest {
    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbc;
    private TemplateFiles files;
    private org.mockito.MockedStatic<LoginHelper> login;
    @BeforeEach void setup()throws Exception {
        var root=java.nio.file.Path.of(System.getProperty("template.snapshot.workspace", "."));
        var yaml=new org.springframework.beans.factory.config.YamlPropertiesFactoryBean();yaml.setResources(new org.springframework.core.io.FileSystemResource(root.resolve("ruoyi-ai/ruoyi-admin/src/main/resources/application-dev.yml")));
        var properties=yaml.getObject();String prefix="spring.datasource.dynamic.datasource.master.",url=properties.getProperty(prefix+"url");
        if(!url.startsWith("jdbc:mysql://127.0.0.1:3306/"))throw new IllegalStateException("Only local test database permitted");
        dataSource=new SingleConnectionDataSource(java.sql.DriverManager.getConnection(url,properties.getProperty(prefix+"username"),properties.getProperty(prefix+"password")),true);
        jdbc=new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TEMPORARY TABLE legal_doc_template_file(version_id VARCHAR(36) PRIMARY KEY,tenant_id VARCHAR(20) NOT NULL,uploaded_by BIGINT NOT NULL,template_id BIGINT,filename VARCHAR(255),source_format VARCHAR(12),source_bytes LONGBLOB,docx_bytes LONGBLOB,blocks_json MEDIUMTEXT,snapshot_json MEDIUMTEXT,bound_at TIMESTAMP(6))");
        files=new TemplateFiles(jdbc,mock(DocumentConversion.class));
        login=mockStatic(LoginHelper.class);login.when(LoginHelper::getTenantId).thenReturn("tenant-a");login.when(LoginHelper::getUserId).thenReturn(7L);
    }
    @AfterEach void close(){if(login!=null)login.close();if(dataSource!=null)dataSource.destroy();}
    private byte[] source()throws Exception{
        try(var doc=new XWPFDocument();var out=new ByteArrayOutputStream()){doc.createParagraph().createRun().setText("姓名：________，金额：________元。");doc.write(out);return out.toByteArray();}
    }
    private TemplateFiles.Upload stage()throws Exception{return files.stage(new MockMultipartFile("file","合成模板.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",source()),"自由描述文书",true);}
    private TemplateSnapshot.Data stored(String id){return TemplateSnapshot.decode(jdbc.queryForObject("SELECT snapshot_json FROM legal_doc_template_file WHERE version_id=?",String.class,id),jdbc.queryForObject("SELECT template_id FROM legal_doc_template_file WHERE version_id=?",Long.class,id));}
    private void matchesWord(String id)throws Exception{
        var data=stored(id);byte[] docx=jdbc.queryForObject("SELECT docx_bytes FROM legal_doc_template_file WHERE version_id=?",byte[].class,id);
        assertEquals(data.blocks(),TemplateLayout.storedFields(docx,List.of()));assertEquals(data.state(),TemplateLayout.state(docx));
    }
    @Test void uploadAnalysisAndHumanConfirmationWriteMatchingSnapshotsAtomically()throws Exception{
        var upload=stage();String id=upload.sourceVersion();assertEquals(upload.blocks(),stored(id).blocks());assertEquals("rules",stored(id).parseStatus());matchesWord(id);
        var fields=upload.blocks().stream().flatMap(b->b.fields().stream()).map(f->new TemplateFields.Field(f.id(),f.blockId(),f.start(),f.end(),f.original(),"合成建议字段",f.kind(),f.unit(),f.presentation(),true)).toList();assertFalse(fields.isEmpty());
        files.saveAnalysis(id,new TemplateFieldAnalysis.Analysis(fields,"AI","合成测试建议，未调用模型"));assertEquals("parsed",stored(id).parseStatus());assertFalse(stored(id).fieldsConfirmed());assertEquals(fields,stored(id).blocks().stream().flatMap(b->b.fields().stream()).toList());matchesWord(id);
        var selected=upload.blocks().stream().map(TemplateLayout.Block::id).toList();files.bind(id,11L,selected,upload.content(),true,fields,true,false);
        assertTrue(stored(id).fieldsConfirmed());assertEquals("confirmed",stored(id).parseStatus());matchesWord(id);
        try(var word=mockStatic(TemplateLayout.class,call->{throw new AssertionError("Read unexpectedly parsed Word: "+call.getMethod().getName());})){
            var config=files.currentConfiguration(11L);assertEquals(fields,config.blocks().stream().flatMap(b->b.fields().stream()).toList());assertTrue(config.fieldsConfirmed());assertEquals(stored(id).blocks(),files.load(id).blocks());
        }
        assertEquals(409,assertThrows(DraftingException.class,()->files.saveAnalysis(id,new TemplateFieldAnalysis.Analysis(fields,"AI","must not overwrite"))).getCode());
    }
    @Test void rawUploadStoresRawStatusAndOriginalBytesWithoutInventingFields()throws Exception{
        byte[] original={1,2,3,4};var upload=files.stage(new MockMultipartFile("file","合成原件.pdf","application/pdf",original),"自由描述文书",false);var data=stored(upload.sourceVersion());
        assertEquals("uploaded",data.parseStatus());assertFalse(data.fieldsConfirmed());assertTrue(data.blocks().isEmpty());assertArrayEquals(original,jdbc.queryForObject("SELECT source_bytes FROM legal_doc_template_file WHERE version_id=?",byte[].class,upload.sourceVersion()));matchesWord(upload.sourceVersion());
    }
    @Test void legacyBackfillPreservesOriginalBinaryFieldsAndReviewStateAndThenStopsParsing()throws Exception{
        byte[] raw=source();var blocks=TemplateLayout.inspect(raw,true);var confirmed=new TemplateLayout.State(false,true,"AI","人工已确认");byte[] docx=TemplateLayout.storeState(TemplateLayout.storeFields(raw,blocks),confirmed);
        String legacy=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(blocks.stream().map(b->new TemplateLayout.Block(b.id(),b.text(),b.editable())).toList());String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO legal_doc_template_file(version_id,tenant_id,uploaded_by,template_id,filename,source_format,source_bytes,docx_bytes,blocks_json,bound_at) VALUES(?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP(6))",id,"tenant-a",7L,22L,"历史模板.docx","docx",raw,docx,legacy);
        var config=files.currentConfiguration(22L);assertEquals(blocks,config.blocks());assertEquals("confirmed",config.parseStatus());assertTrue(config.fieldsConfirmed());
        assertArrayEquals(raw,jdbc.queryForObject("SELECT source_bytes FROM legal_doc_template_file WHERE version_id=?",byte[].class,id));assertArrayEquals(docx,jdbc.queryForObject("SELECT docx_bytes FROM legal_doc_template_file WHERE version_id=?",byte[].class,id));assertEquals(legacy,jdbc.queryForObject("SELECT blocks_json FROM legal_doc_template_file WHERE version_id=?",String.class,id));
        try(var word=mockStatic(TemplateLayout.class,call->{throw new AssertionError("Repeated legacy parse");})){assertEquals(config,files.currentConfiguration(22L));}
        login.when(LoginHelper::getTenantId).thenReturn("tenant-b");assertNull(files.currentConfiguration(22L));assertEquals(404,assertThrows(DraftingException.class,()->files.load(id)).getCode());
    }
    @Test void editingConfirmedFieldsCreatesNewVersionWithoutOverwritingPreviousSnapshot()throws Exception{
        var upload=stage();String old=upload.sourceVersion();var ids=upload.blocks().stream().map(TemplateLayout.Block::id).toList();var fields=upload.blocks().stream().flatMap(b->b.fields().stream()).toList();
        files.bind(old,33L,ids,upload.content(),true,fields,true,false);String before=jdbc.queryForObject("SELECT snapshot_json FROM legal_doc_template_file WHERE version_id=?",String.class,old);byte[] oldDocx=jdbc.queryForObject("SELECT docx_bytes FROM legal_doc_template_file WHERE version_id=?",byte[].class,old);
        var changed=fields.stream().map(f->new TemplateFields.Field(f.id(),f.blockId(),f.start(),f.end(),f.original(),"人工修改字段",f.kind(),f.unit(),f.presentation(),true)).toList();
        files.bind(old,33L,ids,upload.content(),true,changed,true,false);var current=files.currentConfiguration(33L);assertNotEquals(old,current.version());assertTrue(current.fieldsConfirmed());assertEquals(changed,current.blocks().stream().flatMap(b->b.fields().stream()).toList());matchesWord(current.version());
        assertEquals(before,jdbc.queryForObject("SELECT snapshot_json FROM legal_doc_template_file WHERE version_id=?",String.class,old));assertArrayEquals(oldDocx,jdbc.queryForObject("SELECT docx_bytes FROM legal_doc_template_file WHERE version_id=?",byte[].class,old));
    }
    @Test void snapshotCodecRejectsUnknownOrContradictoryStatesWithoutPromotingReview(){
        assertEquals(422,assertThrows(DraftingException.class,()->TemplateSnapshot.decode("{\"schemaVersion\":2,\"blocks\":[]}",1L)).getCode());
        String raw=TemplateSnapshot.encode(List.of(),new TemplateLayout.State(true,false,"",""),null).replace("\"fieldsConfirmed\":false","\"fieldsConfirmed\":true");assertEquals(422,assertThrows(DraftingException.class,()->TemplateSnapshot.decode(raw,null)).getCode());
        assertEquals("rules",TemplateSnapshot.create(List.of(),null,null).parseStatus());assertEquals("confirmed",TemplateSnapshot.create(List.of(),null,1L).parseStatus());
    }
    @Test void racingBackfillCannotOverwriteAConcurrentFieldSave()throws Exception{
        byte[] raw=source();var blocks=TemplateLayout.inspect(raw,true);var oldState=new TemplateLayout.State(false,false,"RULES","旧候选");byte[] oldDocx=TemplateLayout.storeState(TemplateLayout.storeFields(raw,blocks),oldState);
        var changed=blocks.stream().map(b->new TemplateLayout.Block(b.id(),b.text(),b.editable(),b.fields().stream().map(f->new TemplateFields.Field(f.id(),f.blockId(),f.start(),f.end(),f.original(),"并发人工编辑",f.kind(),f.unit(),f.presentation(),true)).toList())).toList();
        var nextState=new TemplateLayout.State(false,true,"AI","人工确认");byte[] nextDocx=TemplateLayout.storeState(TemplateLayout.storeFields(raw,changed),nextState);String nextSnapshot=TemplateSnapshot.encode(changed,nextState,44L);String id=UUID.randomUUID().toString();
        String legacy=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(blocks.stream().map(b->new TemplateLayout.Block(b.id(),b.text(),b.editable())).toList());
        jdbc.update("INSERT INTO legal_doc_template_file(version_id,tenant_id,uploaded_by,template_id,filename,source_format,source_bytes,docx_bytes,blocks_json,bound_at) VALUES(?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP(6))",id,"tenant-a",7L,44L,"并发合成.docx","docx",raw,oldDocx,legacy);
        var racing=new JdbcTemplate(dataSource){boolean saved;@Override public int update(String sql,Object... args){if(!saved&&sql.startsWith("UPDATE legal_doc_template_file SET snapshot_json=? WHERE")){saved=true;super.update("UPDATE legal_doc_template_file SET docx_bytes=?,snapshot_json=? WHERE version_id=? AND tenant_id=?",nextDocx,nextSnapshot,id,"tenant-a");}return super.update(sql,args);}};
        var config=new TemplateFiles(racing,mock(DocumentConversion.class)).currentConfiguration(44L);assertEquals(changed,config.blocks());assertTrue(config.fieldsConfirmed());assertEquals(nextSnapshot,jdbc.queryForObject("SELECT snapshot_json FROM legal_doc_template_file WHERE version_id=?",String.class,id));assertArrayEquals(nextDocx,jdbc.queryForObject("SELECT docx_bytes FROM legal_doc_template_file WHERE version_id=?",byte[].class,id));
    }

}
