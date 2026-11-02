package org.ruoyi.service.legal;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class TemplateStateReaderTest {
    private static final String MANIFEST = "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"xml\" ContentType=\"application/xml\"/></Types>";
    private byte[] archive(String manifest, String state)throws Exception {
        try(var out=new ByteArrayOutputStream();var zip=new ZipOutputStream(out)){
            if(manifest!=null){zip.putNextEntry(new ZipEntry("[Content_Types].xml"));zip.write(manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();}
            zip.putNextEntry(new ZipEntry("word/document.xml"));zip.write("not parsed by the list reader".getBytes());zip.closeEntry();
            if(state!=null){zip.putNextEntry(new ZipEntry("customXml/yulvxing-template-state.json"));zip.write(state.getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();}
            zip.finish();return out.toByteArray();
        }
    }
    @Test void realWordStatesMatchAuthoritativeReaderWithoutChangingReviewMeaning()throws Exception {
        byte[] docx;try(var doc=new XWPFDocument();var out=new ByteArrayOutputStream()){doc.createParagraph().createRun().setText("合成字段");doc.write(out);docx=out.toByteArray();}
        assertNull(TemplateStateReader.read(docx));
        for(var state:List.of(new TemplateLayout.State(true,false,"",""),new TemplateLayout.State(false,false,"AI","待核对"),new TemplateLayout.State(false,false,"RULES","规则候选"),new TemplateLayout.State(false,true,"AI","已确认"))){
            var data=TemplateLayout.storeState(docx,state);assertEquals(TemplateLayout.state(data),TemplateStateReader.read(data));
        }
    }
    @Test void readsOnlySmallMetadataInsteadOfParsingWordBody()throws Exception {
        var expected=new TemplateLayout.State(false,false,"AI","待核对");
        assertEquals(expected,TemplateStateReader.read(archive(MANIFEST,"{\"raw\":false,\"confirmed\":false,\"method\":\"AI\",\"warning\":\"待核对\"}")));
    }
    @Test void corruptMissingOrOversizedMetadataNeverBecomesConfirmed()throws Exception {
        for(var data:List.of(new byte[]{1,2,3},archive(null,null),archive("<bad>",null),archive("<Types/>",null),archive(MANIFEST,"{broken"),archive(MANIFEST," ".repeat(1024*1024+1)))){
            assertEquals(422,assertThrows(DraftingException.class,()->TemplateStateReader.read(data)).getCode());
        }
        assertEquals(422,assertThrows(DraftingException.class,()->TemplateStateReader.read(null)).getCode());
    }
    @Test void deniesDtdAndExternalEntities()throws Exception {
        String xml="<!DOCTYPE Types [<!ENTITY ext SYSTEM 'file:///not-read'>]><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">&ext;</Types>";
        assertEquals(422,assertThrows(DraftingException.class,()->TemplateStateReader.read(archive(xml,null))).getCode());
    }
}
