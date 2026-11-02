package org.ruoyi.controller.legal;

import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.web.handler.GlobalExceptionHandler;
import org.ruoyi.domain.bo.legal.SearchReportInput.*;
import org.ruoyi.domain.vo.legal.SearchReportView.*;
import org.ruoyi.service.legal.SearchReportService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("dev")
class SearchReportControllerTest {
    SearchReportService service;
    MockMvc mvc;
    MockedStatic<LoginHelper> login;
    final Owner owner = new Owner("000000", 101L);
    @BeforeEach void setup() {
        service=mock(SearchReportService.class); login=mockStatic(LoginHelper.class);
        login.when(LoginHelper::getTenantId).thenReturn("000000"); login.when(LoginHelper::getUserId).thenReturn(101L);
        mvc=MockMvcBuilders.standaloneSetup(new SearchReportController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @AfterEach void close() { login.close(); }
    @Test void createUsesAuthenticatedOwnerAndKeepsLongIdsAsStrings() throws Exception {
        long id=2034567890123456789L;
        when(service.create(eq(owner),any())).thenReturn(new Report(id,101L,"标题","draft",null,null,LocalDateTime.now(),LocalDateTime.now(),null,null));
        mvc.perform(post("/legal/report").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"标题\",\"userId\":202}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.id").value(String.valueOf(id)))
            .andExpect(jsonPath("$.data.userId").value("101")).andExpect(jsonPath("$.data.fileOssId").doesNotExist());
        verify(service).create(owner,new Create("标题"));
    }
    @Test void validationAndMissingLoginNeverReachMutation() throws Exception {
        mvc.perform(post("/legal/report").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"  \"}"))
            .andExpect(jsonPath("$.code").value(500));
        verifyNoInteractions(service);
        login.when(LoginHelper::getUserId).thenReturn(null);
        mvc.perform(get("/legal/report/list")).andExpect(jsonPath("$.code").value(401)).andExpect(jsonPath("$.msg").value("请先登录"));
        verifyNoInteractions(service);
    }
    @Test void scopedErrorsKeepClearFailureAndOwnershipMessagesDespiteGlobalMasking() throws Exception {
        when(service.generate(owner,7L)).thenThrow(new ServiceException("报告生成失败：文件存储不可用，请稍后重试",503));
        mvc.perform(post("/legal/report/7/generate")).andExpect(jsonPath("$.code").value(503))
            .andExpect(jsonPath("$.msg").value("报告生成失败：文件存储不可用，请稍后重试"));
        when(service.detail(owner,7L)).thenThrow(new ServiceException("检索报告不存在或无访问权限",404));
        mvc.perform(get("/legal/report/7")).andExpect(jsonPath("$.code").value(404));
    }
    @Test void generateReturnsProtectedDownloadUrl() throws Exception {
        when(service.generate(owner,7L)).thenReturn(new Generated(7L,"generated","/legal/report/7/download"));
        mvc.perform(post("/legal/report/7/generate")).andExpect(jsonPath("$.data.fileUrl").value("/legal/report/7/download"));
        doAnswer(invocation -> { var response=(jakarta.servlet.http.HttpServletResponse)invocation.getArgument(2); response.setHeader("Content-Disposition","attachment; filename=report.docx");response.getOutputStream().write(new byte[]{80,75});return null; })
            .when(service).download(eq(owner),eq(7L),any());
        mvc.perform(get("/legal/report/7/download")).andExpect(status().isOk()).andExpect(header().string("Content-Disposition","attachment; filename=report.docx"))
            .andExpect(content().bytes(new byte[]{80,75}));
    }
}
