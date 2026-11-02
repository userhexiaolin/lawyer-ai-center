package org.ruoyi.service.legal;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Connection-local temporary tables never modify the application's stored templates. */
@EnabledIfSystemProperty(named="template.snapshot.mysql",matches="true")
class TemplateProcessingFilterTest {
    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbc;
    @BeforeEach void setup() throws Exception {
        var root=java.nio.file.Path.of(System.getProperty("template.snapshot.workspace", "."));
        var yaml=new org.springframework.beans.factory.config.YamlPropertiesFactoryBean();
        yaml.setResources(new org.springframework.core.io.FileSystemResource(root.resolve("ruoyi-ai/ruoyi-admin/src/main/resources/application-dev.yml")));
        var properties=yaml.getObject();String prefix="spring.datasource.dynamic.datasource.master.",url=properties.getProperty(prefix+"url");
        if(!url.startsWith("jdbc:mysql://127.0.0.1:3306/"))throw new IllegalStateException("Only local test database permitted");
        dataSource=new SingleConnectionDataSource(java.sql.DriverManager.getConnection(url,properties.getProperty(prefix+"username"),properties.getProperty(prefix+"password")),true);
        jdbc=new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TEMPORARY TABLE legal_doc_template(id BIGINT PRIMARY KEY,tenant_id VARCHAR(20)) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        jdbc.execute("CREATE TEMPORARY TABLE legal_doc_template_file(version_id VARCHAR(36),template_id BIGINT,tenant_id VARCHAR(20),bound_at TIMESTAMP(6),snapshot_json MEDIUMTEXT) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        jdbc.execute("CREATE TEMPORARY TABLE legal_doc_template_field_task(template_id BIGINT,tenant_id VARCHAR(20),state VARCHAR(20),expires_at TIMESTAMP(6)) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        for(long id=1;id<=34;id++)jdbc.update("INSERT INTO legal_doc_template VALUES (?,?)",id,id==34?"tenant-b":"tenant-a");
        source(1,"old","confirmed",true,1);source(1,"latest","uploaded",false,2);
        source(2,"ready","confirmed",true,1);task(2,"ready",1);
        source(3,"partial","uploaded",false,1);task(3,"partial",1);
        source(4,"expired","uploaded",false,1);task(4,"running",-1);
        source(5,"reviewed","confirmed",true,1);task(5,"reviewed",1);
        source(6,"failed","confirmed",true,1);task(6,"failed",1);
        source(7,"stale","confirmed",true,1);task(7,"stale",1);
        source(8,"running","uploaded",false,1);task(8,"running",1);
        source(9,"rules","rules",false,1);
        for(int id=11;id<=34;id++)source(id,"confirmed-"+id,"confirmed",true,1);
        jdbc.update("INSERT INTO legal_doc_template_field_task VALUES (1,'tenant-b','ready',DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL 1 HOUR))");
        jdbc.update("INSERT INTO legal_doc_template_file VALUES ('other-tenant',10,'tenant-b',CURRENT_TIMESTAMP(6),?)","{\"parseStatus\":\"confirmed\",\"fieldsConfirmed\":true}");
    }
    private void source(long id,String version,String status,boolean confirmed,int order) {
        jdbc.update("INSERT INTO legal_doc_template_file VALUES (?,?,'tenant-a',TIMESTAMPADD(SECOND,?,CURRENT_TIMESTAMP(6)),?)",version,id,order,"{\"parseStatus\":\""+status+"\",\"fieldsConfirmed\":"+confirmed+"}");
    }
    private void task(long id,String state,int hours) {
        jdbc.update("INSERT INTO legal_doc_template_field_task VALUES (?,'tenant-a',?,TIMESTAMPADD(HOUR,?,CURRENT_TIMESTAMP(6)))",id,state,hours);
    }
    private List<Long> ids(String state) {
        return jdbc.queryForList("SELECT id FROM legal_doc_template WHERE tenant_id=? AND "+TemplateProcessingFilter.expression(state).replace("{0}","?").replace("{1}","?")+" ORDER BY id",Long.class,"tenant-a","tenant-a","tenant-a",state);
    }
    @Test void latestSourceTaskPrecedenceExpirationAndTenantIsolation() {
        assertEquals(List.of(1L),ids("unparsed"));
        assertEquals(List.of(2L,3L,9L),ids("review"));
        assertEquals(List.of(4L,6L,7L),ids("error"));
        assertEquals(List.of(8L),ids("running"));
        assertEquals(List.of(10L),ids("text"));
        var confirmed=ids("confirmed");assertEquals(24,confirmed.size());assertTrue(confirmed.contains(5L));assertFalse(confirmed.contains(34L));
    }
    @Test void matchingTotalAndSecondPageAreCalculatedAfterFiltering() {
        String where=" WHERE tenant_id=? AND "+TemplateProcessingFilter.expression("confirmed").replace("{0}","?").replace("{1}","?");
        assertEquals(24L,jdbc.queryForObject("SELECT COUNT(*) FROM legal_doc_template"+where,Long.class,"tenant-a","tenant-a","tenant-a","confirmed"));
        assertEquals(List.of(30L,31L,32L,33L),jdbc.queryForList("SELECT id FROM legal_doc_template"+where+" ORDER BY id LIMIT 20 OFFSET 20",Long.class,"tenant-a","tenant-a","tenant-a","confirmed"));
    }
    @Test void emptyTenantAndEveryEmptyStatusReturnZeroWithDifferentTableCollations() {
        for(String state:TemplateProcessingFilter.VALUES){
            String where=" WHERE tenant_id=? AND "+TemplateProcessingFilter.expression(state).replace("{0}","?").replace("{1}","?");
            assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM legal_doc_template"+where,Long.class,"empty","empty","empty",state));
            assertTrue(jdbc.queryForList("SELECT id FROM legal_doc_template"+where+" LIMIT 20",Long.class,"empty","empty","empty",state).isEmpty());
        }
    }
    @Test void invalidFilterCannotBecomeSql() {
        assertEquals(400,assertThrows(DraftingException.class,()->TemplateProcessingFilter.expression("review' OR 1=1")).getCode());
        String sql=TemplateProcessingFilter.expression("review");assertFalse(sql.contains("source_bytes"));assertFalse(sql.contains("docx_bytes"));
    }
    @AfterEach void close() { if(dataSource!=null)dataSource.destroy(); }
}
