package org.ruoyi.controller.legal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.ruoyi.service.legal.DocTemplateFile;
import org.springframework.mock.web.MockMultipartFile;
import org.ruoyi.service.legal.DocTemplateService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class DocTemplateControllerTest {
    @Test void processingFilterIsForwardedThroughTheAuthenticatedListRoute() throws Exception {
        var service=mock(DocTemplateService.class);
        when(service.list(null,null,2,20,"review")).thenReturn(new DocTemplateService.Listing(java.util.List.of(),25));
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new DocTemplateController(service,null)).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/legal/doc-template/list").param("processing","review").param("pageNum","2"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        verify(service).list(null,null,2,20,"review");
    }
    @Test void metadataEndpointUsesExistingEditPermissionAndDoesNotCallFullSave() throws Exception {
        var service = mock(DocTemplateService.class);
        var input = new DocTemplateService.Metadata("模板", "申请书", 0, "1", "备注");
        new DocTemplateController(service, null).updateMetadata(19L, input);
        verify(service).updateMetadata(19L, input);
        verifyNoMoreInteractions(service);
        var method = DocTemplateController.class.getMethod("updateMetadata", Long.class, DocTemplateService.Metadata.class);
        assertArrayEquals(new String[]{"legal:doc-template:edit"}, method.getAnnotation(cn.dev33.satoken.annotation.SaCheckPermission.class).value());
        assertArrayEquals(new String[]{"/{id}/metadata"}, method.getAnnotation(org.springframework.web.bind.annotation.PutMapping.class).value());
        var applyType = DocTemplateController.class.getMethod("updateType", Long.class, DocTemplateService.TypeInput.class);
        assertArrayEquals(new String[]{"legal:doc-template:edit"}, applyType.getAnnotation(cn.dev33.satoken.annotation.SaCheckPermission.class).value());
        assertArrayEquals(new String[]{"/{id}/type"}, applyType.getAnnotation(org.springframework.web.bind.annotation.PutMapping.class).value());
        var typeVersion=DocTemplateController.class.getMethod("typeVersion",Long.class);
        assertArrayEquals(new String[]{"legal:doc-template:edit"},typeVersion.getAnnotation(cn.dev33.satoken.annotation.SaCheckPermission.class).value());
    }
    @Test void uploadReturnsEditablePreviewWithSelectedType() {
        var service = mock(DocTemplateService.class);
        var file = new MockMultipartFile("file", "起诉状.md", "text/plain", new byte[]{1});
        var preview = new org.ruoyi.service.legal.TemplateFiles.Upload("起诉状", "民事起诉状", "# {{原告}}","source-version","md",java.util.List.of());
        when(service.uploadSource(file, "民事起诉状")).thenReturn(preview);
        var result = new DocTemplateController(service,null).upload(file, "民事起诉状");
        assertEquals(preview, result.getData());
        verify(service).uploadSource(file, "民事起诉状");
        verifyNoMoreInteractions(service);
    }
    @Test void creationReturnsStringIdAsDataRatherThanMessage() {
        var service = mock(DocTemplateService.class);
        var input = new DocTemplateService.Input("起诉状", "民事起诉状", "# 起诉状", 1, "0", "");
        when(service.create(input)).thenReturn("2112099436883730433");
        var result = new DocTemplateController(service,null).create(input);
        assertEquals("2112099436883730433", result.getData());
        assertEquals("操作成功", result.getMsg());
    }
    @Test void fieldTaskRoutesBindNamedPathParametersThroughHttp() throws Exception {
        var service=mock(DocTemplateService.class);var tasks=mock(org.ruoyi.service.legal.TemplateFieldTask.class);
        var controller=new DocTemplateController(service,null);org.springframework.test.util.ReflectionTestUtils.setField(controller,"fieldTasks",tasks);
        var status=new org.ruoyi.service.legal.TemplateFieldTask.Status("job","running","正在解析原文件","",null,"","");
        when(tasks.start(19L,false)).thenReturn(status);when(tasks.status(19L)).thenReturn(status);
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        var started=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/legal/doc-template/19/field-analysis").contentType("application/json").content("{}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
        assertTrue(started.getResponse().getContentAsString().contains("job"));verify(tasks).start(19L,false);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/legal/doc-template/19/field-analysis")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());verify(tasks).status(19L);
        var method=DocTemplateController.class.getMethod("analyzeFields",Long.class,DocTemplateController.FieldTaskRequest.class);
        assertArrayEquals(new String[]{"legal:doc-template:edit"},method.getAnnotation(cn.dev33.satoken.annotation.SaCheckPermission.class).value());
        var result=DocTemplateController.class.getMethod("fieldResult",Long.class);
        assertArrayEquals(new String[]{"legal:doc-template:query","legal:doc-template:edit"},result.getAnnotation(cn.dev33.satoken.annotation.SaCheckPermission.class).value());
        assertEquals(cn.dev33.satoken.annotation.SaMode.AND,result.getAnnotation(cn.dev33.satoken.annotation.SaCheckPermission.class).mode());
    }

}
