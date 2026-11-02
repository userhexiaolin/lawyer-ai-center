package org.ruoyi.controller.legal;

import cn.dev33.satoken.annotation.*;
import org.junit.jupiter.api.*;
import org.ruoyi.service.legal.TemplateTypeAnalysis;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class TemplateTypeAnalysisControllerTest {
    @Test void classificationRequiresLoginAndExistingTemplateWritePermission() throws Exception {
        assertNotNull(TemplateTypeAnalysisController.class.getAnnotation(SaCheckLogin.class));
        var method = TemplateTypeAnalysisController.class.getMethod("classify", MultipartFile.class);
        var permission = method.getAnnotation(SaCheckPermission.class);
        assertArrayEquals(new String[]{"legal:doc-template:add", "legal:doc-template:edit"}, permission.value());
        assertEquals(SaMode.OR, permission.mode());
        assertArrayEquals(new String[]{"/classify"}, method.getAnnotation(PostMapping.class).value());
        var analysis = mock(TemplateTypeAnalysis.class);
        var file = new MockMultipartFile("file", "synthetic.txt", "text/plain", new byte[]{1});
        var fallback = new TemplateTypeAnalysis.Analysis(null, "可直接上传"); when(analysis.analyze(file)).thenReturn(fallback);
        assertEquals(fallback, new TemplateTypeAnalysisController(analysis).classify(file).getData());
        verify(analysis).analyze(file);
        var existing = TemplateTypeAnalysisController.class.getMethod("classifyExisting", Long.class);
        assertArrayEquals(new String[]{"legal:doc-template:edit"}, existing.getAnnotation(SaCheckPermission.class).value());
        assertArrayEquals(new String[]{"/{id}/classify"}, existing.getAnnotation(PostMapping.class).value());
    }
}
