package org.ruoyi.service.legal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.entity.legal.LegalDocTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockMultipartFile;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("dev") @EnabledIfSystemProperty(named="template.snapshot.mysql",matches="true")
class TemplateFieldTaskTest {
 SingleConnectionDataSource ds;JdbcTemplate jdbc;TemplateFiles files;DocTemplateService templates;TemplateFieldAnalysis analysis;TemplateFieldTask tasks;org.mockito.MockedStatic<LoginHelper> login;TemplateFiles.Upload original;String revision="revision1";
 @BeforeEach void setup()throws Exception{
  var yaml=new org.springframework.beans.factory.config.YamlPropertiesFactoryBean();yaml.setResources(new org.springframework.core.io.FileSystemResource("ruoyi-ai/ruoyi-admin/src/main/resources/application-dev.yml"));var props=yaml.getObject();String prefix="spring.datasource.dynamic.datasource.master.",url=props.getProperty(prefix+"url");if(!url.startsWith("jdbc:mysql://127.0.0.1:3306/"))throw new IllegalStateException();
  ds=new SingleConnectionDataSource(java.sql.DriverManager.getConnection(url,props.getProperty(prefix+"username"),props.getProperty(prefix+"password")),true);jdbc=new JdbcTemplate(ds);
  jdbc.execute("CREATE TEMPORARY TABLE legal_doc_template_file(version_id VARCHAR(36) PRIMARY KEY,tenant_id VARCHAR(20) NOT NULL,uploaded_by BIGINT NOT NULL,template_id BIGINT,filename VARCHAR(255),source_format VARCHAR(12),source_bytes LONGBLOB,docx_bytes LONGBLOB,blocks_json MEDIUMTEXT,snapshot_json MEDIUMTEXT,bound_at TIMESTAMP(6))");
  String ddl=java.nio.file.Files.readString(java.nio.file.Path.of("ruoyi-ai/docs/script/sql/update/2026-10-26-template-field-tasks.sql"));jdbc.execute(ddl.substring(ddl.indexOf("CREATE TABLE")).replace("CREATE TABLE IF NOT EXISTS","CREATE TEMPORARY TABLE"));
  login=mockStatic(LoginHelper.class);login.when(LoginHelper::getTenantId).thenReturn("tenant-a");login.when(LoginHelper::getUserId).thenReturn(7L);
  files=new TemplateFiles(jdbc,mock(DocumentConversion.class));original=files.stage(new MockMultipartFile("file","合成模板.txt","text/plain","姓名：________，金额：________元。".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"律师函",false);files.bind(original.sourceVersion(),9L,List.of(),"",false,List.of(),false,true);
  templates=mock(DocTemplateService.class);analysis=mock(TemplateFieldAnalysis.class);tasks=new TemplateFieldTask(jdbc,templates,files,analysis,new DataSourceTransactionManager(ds));
  when(templates.typeSnapshot(9L)).thenAnswer(call->new DocTemplateService.TypeSnapshot(9L,"律师函","",original.sourceVersion(),revision));
  doAnswer(call->{if(!Objects.equals(revision,call.getArgument(1)))throw new DraftingException("模板已变化",409);return null;}).when(templates).lockRevision(eq(9L),anyString());
  when(templates.parse(anyString(),anyString())).thenAnswer(call->files.parse(call.getArgument(0),call.getArgument(1)));
  when(templates.get(9L)).thenAnswer(call->{var row=new LegalDocTemplate();row.setId(9L);row.setName("合成模板");row.setStatus("0");row.setContent("");row.setDocumentType("律师函");row.setExpectedRevision(revision);return row;});
 }
 @AfterEach void close(){if(login!=null)login.close();if(ds!=null)ds.destroy();}
 void stub(String method){when(analysis.analyze(anyString())).thenAnswer(call->{String id=call.getArgument(0);assertEquals("正在 AI 识别字段",tasks.status(9L).phase());var config=files.analysisConfiguration(id);return files.saveAnalysis(id,new TemplateFieldAnalysis.Analysis(config.blocks().stream().flatMap(b->b.fields().stream()).toList(),method,"合成待核对建议"));});}
 @Test void realPhasesAndResultSurviveReloadWithoutChangingSavedVersion(){
  stub("AI");var result=tasks.start(9L,false);assertEquals("ready",result.state());assertNotNull(result.resultVersion());assertEquals(original.sourceVersion(),files.currentVersionId(9L));assertFalse(files.currentConfiguration(9L).fieldsConfirmed());
  var otherService=new TemplateFieldTask(jdbc,templates,files,analysis,new DataSourceTransactionManager(ds));assertEquals(result,otherService.status(9L));var editor=otherService.result(9L);assertEquals(result.resultVersion(),editor.getSourceVersion());assertFalse(editor.isFieldsConfirmed());assertFalse(editor.getBlocks().isEmpty());assertEquals("revision1",editor.getExpectedRevision());
  assertEquals(409,assertThrows(DraftingException.class,()->tasks.start(9L,false)).getCode());verify(analysis,times(1)).analyze(anyString());
 }
 @Test void duplicateStartDuringParsingReusesTaskAndModelRunsOnce(){
  when(analysis.analyze(anyString())).thenAnswer(call->{var duplicate=tasks.start(9L,false);assertEquals("running",duplicate.state());var config=files.analysisConfiguration(call.getArgument(0));return files.saveAnalysis(call.getArgument(0),new TemplateFieldAnalysis.Analysis(config.blocks().stream().flatMap(b->b.fields().stream()).toList(),"AI","待核对"));});
  assertEquals("ready",tasks.start(9L,false).state());verify(templates,times(1)).parse(anyString(),anyString());verify(analysis,times(1)).analyze(anyString());
 }
 @Test void modelFallbackIsPartialAndNeverHumanConfirmed(){stub("RULES");assertEquals("partial",tasks.start(9L,false).state());assertFalse(tasks.result(9L).isFieldsConfirmed());}
 @Test void versionConflictNeverPublishesOrOverwritesSavedFields(){when(analysis.analyze(anyString())).thenAnswer(call->{revision="revision2";return new TemplateFieldAnalysis.Analysis(List.of(),"AI","");});assertEquals("stale",tasks.start(9L,false).state());assertEquals(original.sourceVersion(),files.currentVersionId(9L));}
 @Test void fileFailureIsRetryableAndDoesNotExposeExceptionDetails(){doThrow(new IllegalStateException("secret implementation details")).when(templates).parse(anyString(),anyString());var failed=tasks.start(9L,false);assertEquals("failed",failed.state());assertFalse(failed.warning().contains("secret"));assertEquals(original.sourceVersion(),files.currentVersionId(9L));}
 @Test void expiresInterruptedJobsAndIsolatesTenant(){stub("AI");tasks.start(9L,false);jdbc.update("UPDATE legal_doc_template_field_task SET state='running',expires_at=DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 1 SECOND)");assertEquals("failed",tasks.status(9L).state());login.when(LoginHelper::getTenantId).thenReturn("tenant-b");assertNull(tasks.status(9L));}
 @Test void crossEditorWorkingCopyUsesSqlSnapshotAndRetainsOriginal(){stub("AI");var task=tasks.start(9L,false);login.when(LoginHelper::getUserId).thenReturn(8L);String copy;try(var word=mockStatic(TemplateLayout.class,call->{throw new AssertionError("No Word parsing while opening saved fields");})){var result=tasks.result(9L);copy=result.getSourceVersion();assertNotEquals(task.resultVersion(),copy);assertFalse(result.getBlocks().isEmpty());}assertEquals(8L,jdbc.queryForObject("SELECT uploaded_by FROM legal_doc_template_file WHERE version_id=?",Long.class,copy));assertEquals(original.sourceVersion(),files.currentVersionId(9L));}
}
