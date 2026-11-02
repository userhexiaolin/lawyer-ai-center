package org.ruoyi.service.lawyer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.*;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.controller.lawyer.CaseTaskController;
import org.ruoyi.controller.lawyer.LawyerCaseController;
import org.ruoyi.domain.bo.lawyer.CaseTaskInput;
import org.ruoyi.domain.vo.lawyer.CaseTaskView.Task;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("dev")
class CaseTaskControllerTest {
    CaseTaskService tasks;LawyerCaseService cases;MockMvc mvc;
    @BeforeEach void setup() {
        tasks=mock(CaseTaskService.class);cases=mock(LawyerCaseService.class);
        var mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mvc=MockMvcBuilders.standaloneSetup(new CaseTaskController(tasks),new LawyerCaseController(cases)).setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
    }
    @Test void createParsesDeadlineAndSerializesLongIdsAsStrings() throws Exception {
        when(tasks.create(eq(11L),any())).thenAnswer(c->{CaseTaskInput.Create input=c.getArgument(1);assertEquals(LocalDateTime.of(2026,10,20,9,0),input.dueAt());return new Task(90071992547409931L,11L,101L,input.title(),"todo",input.dueAt(),0,0,null,null,List.of());});
        mvc.perform(post("/lawyer/case/11/tasks").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"准备证据\",\"dueAt\":\"2026-10-20 09:00:00\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value("90071992547409931")).andExpect(jsonPath("$.data.caseId").value("11"));
    }
    @Test void bodyValidationBlocksInvalidMinutesAndStatusBeforeService() throws Exception {
        mvc.perform(post("/lawyer/case/task/91/time").contentType(MediaType.APPLICATION_JSON).content("{\"minutes\":0}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/lawyer/case/task/91").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"invalid\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(tasks);
    }
    @Test void explicitUncheckedStateAndBusinessErrorsRemainActionable() throws Exception {
        when(tasks.toggle(91L,92L,false)).thenThrow(new ServiceException("任务不存在或无访问权限",404));
        mvc.perform(post("/lawyer/case/task/91/checklist/92/toggle").contentType(MediaType.APPLICATION_JSON).content("{\"checked\":false}")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(404)).andExpect(jsonPath("$.msg").value("任务不存在或无访问权限"));
        verify(tasks).toggle(91L,92L,false);
    }
    @Test void materialListAndDownloadUseExistingCasePrefixAndReportStorageFailure() throws Exception {
        when(cases.files(11L)).thenReturn(List.of(new LawyerCaseService.Material(71L,"stored.pdf","合同.pdf",".pdf","https://example.org/law",LocalDateTime.of(2026,10,16,9,0),null,"/lawyer/case/11/files/71/download")));
        mvc.perform(get("/lawyer/case/11/files")).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].ossId").value("71")).andExpect(jsonPath("$.data[0].originalName").value("合同.pdf"));
        doThrow(new java.io.IOException("storage offline")).when(cases).download(eq(11L),eq(71L),any());
        mvc.perform(get("/lawyer/case/11/files/71/download")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(503)).andExpect(jsonPath("$.msg").value("材料下载失败：文件存储暂不可用，请稍后重试"));
    }
}
