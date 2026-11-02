package org.ruoyi.service.legal;

import org.junit.jupiter.api.*;
import org.ruoyi.common.core.domain.dto.OssDTO;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.core.service.OssService;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class SearchReportFilesTest {
    @Test void reusesExistingOssAndNeverExposesVendorErrors() {
        var oss=mock(OssService.class); var files=new SearchReportFiles(oss);var file=new File("report.docx");var asset=new OssDTO();asset.setOssId(42L);
        when(oss.uploadFile(file)).thenReturn(asset);assertEquals(42L,files.upload(file).getOssId());verify(oss).uploadFile(file);
        when(oss.uploadFile(file)).thenThrow(new IllegalStateException("private vendor detail"));
        var error=assertThrows(ServiceException.class,()->files.upload(file));assertEquals(503,error.getCode());assertFalse(error.getMessage().contains("private vendor"));
        when(oss.deleteFile(42L)).thenReturn(true);files.discard(42L);verify(oss).deleteFile(42L);
    }
}
