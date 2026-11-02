package org.ruoyi.service.legal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named="template.snapshot.mysql",matches="true")
class TemplateTypeDecisionStoreTest {
 private SingleConnectionDataSource source;
 private JdbcTemplate jdbc;
 private org.mockito.MockedStatic<LoginHelper> login;
 @BeforeEach void setup()throws Exception{
  var root=java.nio.file.Path.of(System.getProperty("template.snapshot.workspace","."));
  var yaml=new org.springframework.beans.factory.config.YamlPropertiesFactoryBean();yaml.setResources(new org.springframework.core.io.FileSystemResource(root.resolve("ruoyi-ai/ruoyi-admin/src/main/resources/application-dev.yml")));
  var p=yaml.getObject();String prefix="spring.datasource.dynamic.datasource.master.",url=p.getProperty(prefix+"url");
  if(!url.startsWith("jdbc:mysql://127.0.0.1:3306/"))throw new IllegalStateException("Only local temporary database permitted");
  source=new SingleConnectionDataSource(java.sql.DriverManager.getConnection(url+"&connectTimeout=5000&socketTimeout=10000",p.getProperty(prefix+"username"),p.getProperty(prefix+"password")),true);
  jdbc=new JdbcTemplate(source);
  String ddl=java.nio.file.Files.readString(root.resolve("ruoyi-ai/docs/script/sql/update/2026-10-27-template-type-decisions.sql"));
  ddl=ddl.substring(ddl.indexOf("CREATE TABLE")).replace("CREATE TABLE IF NOT EXISTS","CREATE TEMPORARY TABLE");jdbc.execute(ddl);
  login=mockStatic(LoginHelper.class);login.when(LoginHelper::getUserId).thenReturn(7L);login.when(LoginHelper::getTenantId).thenReturn("tenant-a");
 }
 @AfterEach void close(){if(login!=null)login.close();if(source!=null)source.destroy();}
 @Test void firstSuggestionSurvivesAnotherStoreInstanceAndAnInsertConflict(){
  var store=new TemplateTypeDecisionStore(jdbc);String key="a".repeat(64);var first=new TemplateTypeDecisionStore.Decision("律师函","AI","请履行义务");
  assertEquals(first,store.retain(key,"b".repeat(64),7L,"test-qwen",TemplateTypePolicy.VERSION,first));
  var conflicting=new TemplateTypeDecisionStore.Decision("合同文书","AI","双方约定");
  assertEquals(first,new TemplateTypeDecisionStore(jdbc).retain(key,"b".repeat(64),7L,"test-qwen",TemplateTypePolicy.VERSION,conflicting));
  assertEquals(first,new TemplateTypeDecisionStore(jdbc).find(key).orElseThrow());
  assertEquals(1L,jdbc.queryForObject("SELECT COUNT(*) FROM legal_doc_template_type_decision",Long.class));
 }
 @Test void identicalFingerprintsAreIsolatedByTenantAndMissingLoginIsDenied(){
  var store=new TemplateTypeDecisionStore(jdbc);String key="a".repeat(64);var a=new TemplateTypeDecisionStore.Decision("律师函","TITLE","律师函");
  store.retain(key,"b".repeat(64),7L,"model",TemplateTypePolicy.VERSION,a);
  login.when(LoginHelper::getTenantId).thenReturn("tenant-b");assertTrue(store.find(key).isEmpty());
  var b=new TemplateTypeDecisionStore.Decision("合同文书","TITLE","合同");store.retain(key,"b".repeat(64),7L,"model",TemplateTypePolicy.VERSION,b);assertEquals(b,store.find(key).orElseThrow());
  login.when(LoginHelper::getTenantId).thenReturn("tenant-a");assertEquals(a,store.find(key).orElseThrow());
  login.when(LoginHelper::getUserId).thenReturn(null);assertEquals(401,assertThrows(DraftingException.class,()->store.find(key)).getCode());
 }
}
