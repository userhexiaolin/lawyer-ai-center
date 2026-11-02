package org.ruoyi.service.lawyer;

import org.junit.jupiter.api.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.encryption.*;
import org.ruoyi.service.legal.DocumentConversion;
import org.ruoyi.common.core.exception.ServiceException;
import java.io.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class DossierFileReaderTest {
    static byte[] pdf(int pages,boolean thirdBlank)throws Exception{
        try(var doc=new PDDocument();var out=new ByteArrayOutputStream()){
            for(int i=1;i<=pages;i++){var page=new PDPage();doc.addPage(page);if(thirdBlank&&i==3)continue;
                try(var stream=new PDPageContentStream(doc,page)){stream.beginText();stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),20);stream.newLineAtOffset(50,650);stream.showText("SYNTHETIC PAGE "+i+" PAYMENT 50000.00");stream.endText();}}
            doc.save(out);return out.toByteArray();
        }
    }
    @Test void oneUnreadablePageCannotBecomeWholeFileSuccess()throws Exception{
        var ocr=mock(DossierOcr.class);when(ocr.recognize(any())).thenThrow(new DossierReadException("OCR_TIMEOUT","本页超时"));
        try(var source=new DossierFileReader(ocr,mock(DocumentConversion.class)).open(pdf(10,true),"application/pdf")){
            assertEquals(10,source.count());int good=0,bad=0;
            for(int i=1;i<=10;i++){var p=source.read(i);if(p.usable())good++;else{bad++;assertEquals(3,i);assertEquals("OCR_TIMEOUT",p.errorCode());}}
            assertEquals(9,good);assertEquals(1,bad);verify(ocr,times(1)).recognize(any());
        }
    }
    @Test void emptyRecognitionNeedsManualBlankConfirmation()throws Exception{
        var ocr=mock(DossierOcr.class);when(ocr.recognize(any())).thenReturn("");
        try(var source=new DossierFileReader(ocr,mock(DocumentConversion.class)).open(pdf(3,true),"application/pdf")){
            assertEquals("EMPTY_UNCONFIRMED",source.read(3).errorCode());assertFalse(source.read(3).usable());
        }
    }
    @Test void unexpectedOcrFailureStaysOnItsPhysicalPageWithoutLeakingException()throws Exception{
        var reader=new DossierFileReader(image->{throw new IllegalStateException("SYNTHETIC PRIVATE OCR PAYLOAD");},mock(DocumentConversion.class));
        try(var source=reader.open(pdf(3,true),"application/pdf")){
            assertTrue(source.read(1).usable());var failed=source.read(3);
            assertFalse(failed.usable());assertEquals("PAGE_READ_FAILED",failed.errorCode());
            assertFalse(failed.errorMessage().contains("PRIVATE"));assertTrue(failed.text().isEmpty());
        }
    }
    @Test void encryptedAndCorruptDocumentsHaveDistinctReasons()throws Exception{
        var reader=new DossierFileReader(mock(DossierOcr.class),mock(DocumentConversion.class));
        byte[] bytes;
        try(var doc=org.apache.pdfbox.Loader.loadPDF(pdf(1,false));var out=new ByteArrayOutputStream()){
            var policy=new StandardProtectionPolicy("owner-password","open-password",new AccessPermission());policy.setEncryptionKeyLength(128);doc.protect(policy);doc.save(out);bytes=out.toByteArray();
        }
        byte[] encrypted=bytes;
        assertEquals("ENCRYPTED_PDF",assertThrows(DossierReadException.class,()->reader.open(encrypted,"application/pdf")).errorCode());
        assertEquals("MALFORMED_DOCUMENT",assertThrows(DossierReadException.class,()->reader.open("%PDF-broken".getBytes(),"application/pdf")).errorCode());
    }
    @Test void byteFingerprintAndMagicAreAuthoritative()throws Exception{
        byte[] a=pdf(1,false),b=pdf(2,false);
        assertNotEquals(DossierFiles.hash(a),DossierFiles.hash(b));assertEquals("application/pdf",DossierFiles.validate("同名.pdf",a));
        assertThrows(ServiceException.class,()->DossierFiles.validate("伪装.pdf",new byte[]{80,75,3,4}));
        assertThrows(ServiceException.class,()->DossierFiles.validate("空.pdf",new byte[0]));
        assertThrows(ServiceException.class,()->DossierFiles.declared("过大.pdf",DossierFiles.MAX_BYTES+1L,"a".repeat(64)));
        assertThrows(ServiceException.class,()->DossierFiles.validate("压缩包.zip",a));
    }
    @Test void officeZipTraversalAndBombAreRejected()throws Exception{
        for(String malicious:new String[]{"../payload","word/vbaProject.bin","word/huge.xml"}){
            var out=new ByteArrayOutputStream();
            try(var zip=new ZipOutputStream(out)){
                for(String name:new String[]{"[Content_Types].xml","word/document.xml",malicious}){
                    zip.putNextEntry(new ZipEntry(name));zip.write(name.equals("word/huge.xml")?new byte[2*1024*1024]:new byte[]{1});zip.closeEntry();
                }
            }
            byte[] bytes=out.toByteArray();assertThrows(ServiceException.class,()->DossierFiles.validate("不安全.docx",bytes));
        }
    }
    @Test void physicalPagePreviewReturnsActualPngAndBoundsAreChecked()throws Exception{
        try(var source=new DossierFileReader(mock(DossierOcr.class),mock(DocumentConversion.class)).open(pdf(3,false),"application/pdf")){
            byte[] png=source.preview(2);assertTrue(png.length>100);assertEquals(137,png[0]&255);assertThrows(IOException.class,()->source.preview(4));
        }
    }
    @Test void brokenGraphicsCannotSilentlyBecomeACompletePreviewWhileReadableTextAndOriginalRemain()throws Exception{
        var root=java.nio.file.Path.of(System.getProperty("dossier.workspace"));
        byte[] bytes=java.nio.file.Files.readAllBytes(root.resolve("docs/product/o001/evidence/sequential-20261023/samples/UI-text-readable-preview-failed.pdf"));
        var ocr=mock(DossierOcr.class);
        try(var source=new DossierFileReader(ocr,mock(DocumentConversion.class)).open(bytes,"application/pdf")){
            var page=source.read(1);
            assertTrue(page.usable());
            assertEquals("SYNTHETIC PREVIEW FALLBACK PAYMENT 123.45",page.text());
            assertEquals("failed",page.previewStatus());
            assertThrows(IOException.class,()->source.preview(1));
            verifyNoInteractions(ocr);
        }
        assertEquals("application/pdf",DossierFiles.validate("original.pdf",bytes));
    }
}
