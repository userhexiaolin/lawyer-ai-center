package org.ruoyi.service.lawyer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.service.OssService;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.controller.chat.ContractReviewController;
import org.ruoyi.domain.entity.lawyer.LawyerCase;
import java.util.*;
@org.junit.jupiter.api.Tag("dev")
class DossierServiceTest {
    @Test void limitsAndDuplicateIdsRejected(){assertThrows(ServiceException.class,()->DossierService.validateCount(List.of(1L,2L),1));assertThrows(ServiceException.class,()->DossierService.validateCount(List.of(1L,1L),6));}
    @Test void foreignAssociationRejectedBeforeStorageRead(){var cases=mock(LawyerCaseService.class);var oss=mock(OssService.class);var reader=mock(ContractReviewController.class);var service=new DossierService(cases,reader,oss);when(cases.file(1L,99L)).thenThrow(new ServiceException("无权限"));assertThrows(ServiceException.class,()->service.parse(1L,List.of(99L)));verifyNoInteractions(oss,reader);}
    @Test void parsedFilesHaveSourcesAndFailuresAreVisible()throws Exception{var cases=mock(LawyerCaseService.class);var oss=mock(OssService.class);var reader=mock(ContractReviewController.class);var service=new DossierService(cases,reader,oss);var value=new LawyerCase();value.setTitle("测试案件");when(cases.detail(1L)).thenReturn(new LawyerCaseService.CaseDetail(value,List.of(),List.of()));for(long id:List.of(2L,3L)){when(cases.file(1L,id)).thenReturn(new LawyerCaseService.Material(id,"file","材料"+id+".docx","docx",null,null,null,null));when(oss.readFileBytes(eq(id),anyInt())).thenReturn(new byte[]{1});}when(reader.content(any())).thenReturn(R.ok(new ContractReviewController.ContractText("借款已支付"))).thenThrow(new ServiceException("损坏"));var result=service.parse(1L,List.of(2L,3L));assertEquals(5,result.totalCharacters());assertEquals("success",result.documents().get(0).status());assertEquals("failed",result.documents().get(1).status());assertFalse(result.documents().get(1).error().isBlank());}
    @Test void overCharacterLimitFailsClearly()throws Exception{var cases=mock(LawyerCaseService.class);var oss=mock(OssService.class);var reader=mock(ContractReviewController.class);var service=new DossierService(cases,reader,oss);var value=new LawyerCase();when(cases.detail(1L)).thenReturn(new LawyerCaseService.CaseDetail(value,List.of(),List.of()));when(cases.file(1L,2L)).thenReturn(new LawyerCaseService.Material(2L,"file","材料.docx","docx",null,null,null,null));when(oss.readFileBytes(eq(2L),anyInt())).thenReturn(new byte[]{1});when(reader.content(any())).thenReturn(R.ok(new ContractReviewController.ContractText("字".repeat(50001))));var e=assertThrows(ServiceException.class,()->service.parse(1L,List.of(2L)));assertTrue(e.getMessage().contains("总字数超过"));}
}
