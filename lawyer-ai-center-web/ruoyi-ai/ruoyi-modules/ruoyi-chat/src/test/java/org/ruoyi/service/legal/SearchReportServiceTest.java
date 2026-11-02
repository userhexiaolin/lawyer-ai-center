package org.ruoyi.service.legal;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.*;
import org.ruoyi.common.core.domain.dto.OssDTO;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.domain.bo.legal.SearchReportInput.*;
import org.ruoyi.domain.bo.legal.SearchReportInput.Order;
import org.ruoyi.domain.vo.legal.SearchReportView.*;
import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class SearchReportServiceTest {
    final SearchReportRepository repository = mock(SearchReportRepository.class);
    final SearchReportSources sources = mock(SearchReportSources.class);
    final SearchReportWord word = mock(SearchReportWord.class);
    final SearchReportFiles files = mock(SearchReportFiles.class);
    final SearchReportService service = new SearchReportService(repository, sources, word, files);
    final Owner owner = new Owner("000000", 1L);
    final Report report = new Report(7L,1L,"检索报告","draft",null,null,LocalDateTime.now(),LocalDateTime.now(),null,null);
    static Item item(Long id) { return new Item(id,7L,"custom_text",null,null,"标题","快照正文",null,null,null,null,id.intValue(),LocalDateTime.now()); }
    @BeforeEach void setup() { when(repository.owned(owner,7L,true)).thenReturn(report); when(repository.owned(owner,7L,false)).thenReturn(report); }
    @Test void everyIdOperationRejectsForeignReportBeforeItemSourceOrFileAccess() throws Exception {
        when(repository.owned(eq(owner),eq(9L),anyBoolean())).thenThrow(new ServiceException("不存在或无访问权限",404));
        for (org.junit.jupiter.api.function.Executable call : List.<org.junit.jupiter.api.function.Executable>of(
            () -> service.detail(owner,9L), () -> service.update(owner,9L,new Update("标题",null,null)),
            () -> service.delete(owner,9L), () -> service.add(owner,9L,null), () -> service.order(owner,9L,new Order(List.of())),
            () -> service.deleteItem(owner,9L,2L), () -> service.generate(owner,9L), () -> service.download(owner,9L,mock(HttpServletResponse.class))))
            assertEquals(404, assertThrows(ServiceException.class, call).getCode());
        verifyNoInteractions(sources,word,files); verify(repository,never()).items(anyLong());
    }
    @Test void fullOrderingRejectsDuplicatesMissingAndForeignIdsBeforeAnyWrite() {
        when(repository.items(7L)).thenReturn(List.of(item(1L),item(2L)));
        for (var ids : List.of(List.of(1L), List.of(1L,1L), List.of(1L,99L)))
            assertEquals(400, assertThrows(ServiceException.class, () -> service.order(owner,7L,new Order(ids))).getCode());
        assertEquals(400, assertThrows(ServiceException.class, () -> service.order(owner,7L,new Order(java.util.Arrays.asList(1L,null)))).getCode());
        verify(repository,never()).order(anyLong(),anyLong(),anyInt()); verify(repository,never()).draft(any(),anyLong(),any());
        service.order(owner,7L,new Order(List.of(2L,1L)));
        verify(repository).order(7L,2L,0); verify(repository).order(7L,1L,1); verify(repository).draft(eq(owner),eq(7L),any());
    }
    @Test void duplicateLawReturnsExistingItemWithoutInsertionOrInvalidatingGeneratedDocument() {
        var snapshot = new SearchReportSources.Snapshot("law",null,null,"法规","正文","effective","测试法规","第1条",null,"key");
        when(sources.resolve(any())).thenReturn(snapshot); when(repository.duplicateLaw(7L,"key")).thenReturn(item(11L));
        var added = service.add(owner,7L,new AddItem("law",null,null,"法规","正文",null,"测试法规","1",null,null));
        assertTrue(added.duplicate()); assertEquals(11L,added.item().id());
        verify(repository,never()).insert(any(),any()); verify(repository,never()).draft(any(),anyLong(),any());
    }
    @Test void emptyReportAndFailedUploadNeverBecomeGeneratedAndTemporaryFileIsRemoved() {
        when(repository.items(7L)).thenReturn(List.of());
        assertEquals(400, assertThrows(ServiceException.class, () -> service.generate(owner,7L)).getCode());
        when(repository.items(7L)).thenReturn(List.of(item(1L))); when(word.generate(any(),anyList(),any())).thenReturn(new byte[]{1,2});
        File[] temporary = new File[1]; when(files.upload(any())).thenAnswer(invocation -> { temporary[0]=invocation.getArgument(0); throw new ServiceException("报告生成失败：文件存储不可用",503); });
        assertEquals(503, assertThrows(ServiceException.class, () -> service.generate(owner,7L)).getCode());
        assertNotNull(temporary[0]); assertFalse(temporary[0].exists()); verify(repository,never()).generated(any(),anyLong(),anyLong(),any());
    }
    @Test void failureToPersistGenerationCompensatesUploadedAssetOutsideTransactionsToo() {
        when(repository.items(7L)).thenReturn(List.of(item(1L))); when(word.generate(any(),anyList(),any())).thenReturn(new byte[]{1});
        var asset = new OssDTO(); asset.setOssId(42L); when(files.upload(any())).thenReturn(asset);
        doThrow(new IllegalStateException("db failure")).when(repository).generated(any(),anyLong(),anyLong(),any());
        assertEquals(500, assertThrows(ServiceException.class, () -> service.generate(owner,7L)).getCode()); verify(files).discard(42L);
    }
}
