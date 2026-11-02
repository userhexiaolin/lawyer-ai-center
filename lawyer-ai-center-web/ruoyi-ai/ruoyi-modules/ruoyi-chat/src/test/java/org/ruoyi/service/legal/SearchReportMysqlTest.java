package org.ruoyi.service.legal;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.ruoyi.common.core.domain.dto.OssDTO;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.domain.bo.legal.SearchReportInput.*;
import org.ruoyi.domain.bo.legal.SearchReportInput.Order;
import org.ruoyi.domain.vo.legal.SearchReportView.*;
import org.ruoyi.domain.vo.knowledge.*;
import org.ruoyi.service.knowledge.LegalCitationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Uses only connection-local TEMPORARY tables: existing persistent reports are never read/written. */
@Tag("dev")
@EnabledIfSystemProperty(named = "search.report.mysql", matches = "true")
class SearchReportMysqlTest {
    final Owner a = new Owner("000000", 101L), b = new Owner("000000", 202L), otherTenant = new Owner("111111", 101L);
    SingleConnectionDataSource dataSource;
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    SearchReportRepository repository;
    SearchReportService service;
    SearchReportFiles files;
    LegalCitationService fragments;
    byte[] document;
    static String setting(String config, String name) {
        var matcher = Pattern.compile("(?m)^          " + name + ": (.+)$").matcher(config);
        if (!matcher.find()) throw new IllegalStateException("Missing local datasource configuration");
        return matcher.group(1).strip();
    }
    @BeforeEach void setup() throws Exception {
        Path workspace = Path.of(System.getProperty("search.report.workspace")).toAbsolutePath().normalize();
        String config = Files.readString(workspace.resolve("ruoyi-ai/ruoyi-admin/src/main/resources/application-dev.yml"));
        Connection connection = DriverManager.getConnection(setting(config,"url"), setting(config,"username"), setting(config,"password"));
        dataSource = new SingleConnectionDataSource(connection, true); jdbc = new JdbcTemplate(dataSource);
        String migration = Files.readString(workspace.resolve("ruoyi-ai/docs/script/sql/update/2026-10-17-legal-search-report.sql"));
        migration = migration.replaceAll("(?m)--.*$", "").replace("CREATE TABLE IF NOT EXISTS", "CREATE TEMPORARY TABLE IF NOT EXISTS");
        for (String sql : migration.split(";")) if (!sql.isBlank()) jdbc.execute(sql);
        repository = spy(new SearchReportRepository(jdbc));
        fragments = mock(LegalCitationService.class); files = mock(SearchReportFiles.class);
        var asset = new OssDTO(); asset.setOssId(42L);
        when(files.upload(any())).thenAnswer(invocation -> { document = Files.readAllBytes(((java.io.File)invocation.getArgument(0)).toPath()); return asset; });
        doAnswer(invocation -> { MockHttpServletResponse response=invocation.getArgument(1); response.getOutputStream().write(document); return null; }).when(files).download(eq(42L),any());
        service = new SearchReportService(repository, new SearchReportSources(fragments), new SearchReportWord(), files);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }
    @AfterEach void close() { if (dataSource != null) dataSource.destroy(); }
    <T> T call(Supplier<T> action) { return tx.execute(status -> action.get()); }
    void run(Runnable action) { tx.executeWithoutResult(status -> action.run()); }
    Report create(Owner owner) { return call(() -> service.create(owner,new Create("个人检索报告"))); }
    Added add(Owner owner, Long id, String title) { return call(() -> service.add(owner,id,new AddItem("custom_text",null,null,title,"快照："+title,null,null,null,null,null))); }

    @Test void realSqlCrudDedupFullOrderAndLogicalDeletion() {
        Long id = create(a).id();
        var first = add(a,id,"段落一").item();
        var law = call(() -> service.add(a,id,SearchReportSourcesTest.law("《测试法规》","第１２条","repealed")));
        var duplicate = call(() -> service.add(a,id,SearchReportSourcesTest.law("测试法规","12","effective")));
        assertTrue(duplicate.duplicate()); assertEquals(law.item().id(),duplicate.item().id());
        assertEquals("repealed",duplicate.item().lawStatus()); assertEquals(2,call(() -> service.detail(a,id)).items().size());
        run(() -> service.order(a,id,new Order(List.of(law.item().id(),first.id()))));
        assertEquals(List.of(law.item().id(),first.id()),call(() -> service.detail(a,id)).items().stream().map(Item::id).toList());
        assertThrows(ServiceException.class, () -> run(() -> service.order(a,id,new Order(List.of(first.id())))));
        assertEquals(law.item().id(),call(() -> service.detail(a,id)).items().get(0).id());
        run(() -> service.deleteItem(a,id,first.id())); assertEquals(1,call(() -> service.detail(a,id)).items().size());
        run(() -> service.update(a,id,new Update("更新后的标题","背景快照","结论快照")));
        assertEquals("更新后的标题",call(() -> service.detail(a,id)).report().title());
        assertEquals(1,call(() -> service.list(a,1,20)).getTotal());
        run(() -> service.delete(a,id)); assertEquals(0,call(() -> service.list(a,1,20)).getTotal());
        assertThrows(ServiceException.class, () -> call(() -> service.detail(a,id)));
        assertEquals(2,jdbc.queryForObject("SELECT del_flag FROM legal_search_report WHERE id=?",Integer.class,id));
    }
    @Test void noUserOrTenantCanReadMutateGenerateOrDownloadAnotherOwnersReport() throws Exception {
        Long id=create(b).id(); var item=add(b,id,"乙的条目").item();
        create(a);
        for (Owner outsider : List.of(a,otherTenant)) {
            for (org.junit.jupiter.api.function.Executable action : List.<org.junit.jupiter.api.function.Executable>of(
                () -> call(() -> service.detail(outsider,id)), () -> run(() -> service.update(outsider,id,new Update("篡改",null,null))),
                () -> run(() -> service.delete(outsider,id)), () -> add(outsider,id,"注入"),
                () -> run(() -> service.order(outsider,id,new Order(List.of(item.id())))),
                () -> run(() -> service.deleteItem(outsider,id,item.id())), () -> call(() -> service.generate(outsider,id)),
                () -> service.download(outsider,id,new MockHttpServletResponse())))
                assertEquals(404,assertThrows(ServiceException.class,action).getCode());
        }
        assertEquals(1,call(() -> service.list(a,1,20)).getTotal()); assertEquals(0,call(() -> service.list(otherTenant,1,20)).getTotal());
        assertEquals("乙的条目",call(() -> service.detail(b,id)).items().get(0).snapshotTitle()); verifyNoInteractions(files);
    }
    @Test void docxUsesCapturedFragmentAndSortedSnapshotsAndDownloadRequiresOwner() throws Exception {
        Long id=create(a).id(); run(() -> service.update(a,id,new Update(null,"背景：金额7319元","结论：核对条款")));
        var fragment=KnowledgeRetrievalVo.builder().id("fid").content("法规原始快照 <原文>\n第二行").build();
        fragment.setReference(new LegalReference("law","测试法规","12","repealed",null,null,null,"https://example.org/law","fid"));
        when(fragments.detail("fid")).thenReturn(fragment);
        var law=call(() -> service.add(a,id,new AddItem("law",null,"第十二条",null,null,null,null,null,null,"fid"))).item();
        var text=add(a,id,"优先展示的观点").item();
        fragment.setContent("后续知识库内容已改变，不应进入报告");
        run(() -> service.order(a,id,new Order(List.of(text.id(),law.id()))));
        var result=call(() -> service.generate(a,id)); assertEquals("generated",result.status()); assertTrue(result.fileUrl().endsWith("/download"));
        assertEquals("generated",call(() -> service.detail(a,id)).report().status());
        try (var doc=new XWPFDocument(new ByteArrayInputStream(document)); var extractor=new XWPFWordExtractor(doc)) {
            String body=extractor.getText();
            for (String value : List.of("个人检索报告","生成时间：","背景：金额7319元","结论：核对条款","《测试法规》第12条","已失效（repealed）","法规原始快照 <原文>","第二行")) assertTrue(body.contains(value),value);
            assertTrue(body.indexOf("优先展示的观点")<body.indexOf("法规原始快照")); assertFalse(body.contains("后续知识库内容已改变"));
        }
        verify(fragments,times(1)).detail("fid");
        var response=new MockHttpServletResponse(); service.download(a,id,response); assertArrayEquals(document,response.getContentAsByteArray());
        assertEquals(404,assertThrows(ServiceException.class,() -> service.download(b,id,new MockHttpServletResponse())).getCode());
        run(() -> service.update(a,id,new Update(null,null,"修改后的结论")));
        assertEquals("draft",call(() -> service.detail(a,id)).report().status());
        assertEquals(409,assertThrows(ServiceException.class,() -> service.download(a,id,new MockHttpServletResponse())).getCode());
    }
    @Test void failedStorageAndPersistenceRetainDraftAndRollbackUploadedAsset() {
        Long id=create(a).id(); add(a,id,"应保留的快照");
        doThrow(new ServiceException("报告生成失败：文件存储不可用",503)).when(files).upload(any());
        assertEquals(503,assertThrows(ServiceException.class,() -> call(() -> service.generate(a,id))).getCode());
        assertEquals("draft",call(() -> service.detail(a,id)).report().status());
        var asset=new OssDTO();asset.setOssId(42L); doReturn(asset).when(files).upload(any());
        doThrow(new IllegalStateException("simulated persistence failure")).when(repository).generated(any(),eq(id),eq(42L),any());
        assertThrows(ServiceException.class,() -> call(() -> service.generate(a,id))); verify(files).discard(42L);
        assertEquals("draft",call(() -> service.detail(a,id)).report().status());
        assertNull(call(() -> service.detail(a,id)).report().fileOssId()); assertEquals(1,call(() -> service.detail(a,id)).items().size());
    }
}
