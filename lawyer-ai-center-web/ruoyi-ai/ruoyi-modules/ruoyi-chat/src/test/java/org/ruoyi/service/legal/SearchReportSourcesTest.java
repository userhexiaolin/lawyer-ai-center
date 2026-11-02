package org.ruoyi.service.legal;

import org.junit.jupiter.api.*;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.domain.bo.legal.SearchReportInput.AddItem;
import org.ruoyi.domain.vo.knowledge.*;
import org.ruoyi.service.knowledge.LegalCitationService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class SearchReportSourcesTest {
    final LegalCitationService fragments = mock(LegalCitationService.class);
    final SearchReportSources sources = new SearchReportSources(fragments);
    static AddItem law(String name, String article, String status) {
        return new AddItem("law", null, null, "法规快照", "原始文本\n第二行 <测试>", status, name, article, null, null);
    }
    @Test void lawKeyNormalizesBookBracketsWhitespaceAndNumericArticleFormatting() {
        var a = sources.resolve(law("《测试法规》", " 第１２条 ", "repealed"));
        var b = sources.resolve(law("测试法规", "12", null));
        assertEquals(a.lawKey(), b.lawKey()); assertEquals("第12条", a.articleNo());
        assertEquals("repealed", a.lawStatus()); assertEquals("unknown", b.lawStatus());
        assertNotEquals(a.lawKey(), sources.resolve(law("测试法规", "13", null)).lawKey());
        assertNull(sources.resolve(law(null, null, null)).lawKey());
    }
    @Test void partialIdentityIsPreservedWithoutGuessingAndInvalidTextIsRejected() {
        var partial = sources.resolve(law("测试法规", null, null));
        assertEquals("测试法规", partial.lawName()); assertNull(partial.articleNo()); assertNull(partial.lawKey());
        assertThrows(ServiceException.class, () -> sources.resolve(law("测试法规", "9".repeat(100), null)));
        assertThrows(ServiceException.class, () -> sources.resolve(law("测试法规", "1", "bad")));
        assertThrows(ServiceException.class, () -> sources.resolve(new AddItem("custom_text", null, null, "标题", "含\u0000字符", null, null, null, null, null)));
        assertThrows(ServiceException.class, () -> sources.resolve(new AddItem("custom_text", null, null, "标题", "正文", null, null, null, "javascript:alert(1)", null)));
    }
    @Test void fragmentCaptureUsesVerifiedContentAndMetadataInsteadOfCallerOverrides() {
        var fragment = KnowledgeRetrievalVo.builder().id("fid").sourceName("文件名").content("知识库原始快照").build();
        fragment.setReference(new LegalReference("law", "测试法规", "12", "repealed", null, null, null, "https://example.org/law", "fid"));
        when(fragments.detail("fid")).thenReturn(fragment);
        var snapshot = sources.resolve(new AddItem("law", "wrong", "第2段", "伪造标题", "替换正文", "effective", "其他法规", "3", null, "fid"));
        assertEquals("知识库原始快照", snapshot.content()); assertEquals("测试法规", snapshot.lawName());
        assertEquals("第12条", snapshot.articleNo()); assertEquals("repealed", snapshot.lawStatus()); assertEquals("fid", snapshot.refId());
        verify(fragments).detail("fid");
    }
    @Test void inaccessibleFragmentCannotFallBackToUserSuppliedContent() {
        when(fragments.detail("private")).thenThrow(new ServiceException("无访问权限",403));
        var error = assertThrows(ServiceException.class, () -> sources.resolve(new AddItem("case_section", null, null, "标题", "正文", null, null, null, null, "private")));
        assertEquals(403, error.getCode());
    }
}
