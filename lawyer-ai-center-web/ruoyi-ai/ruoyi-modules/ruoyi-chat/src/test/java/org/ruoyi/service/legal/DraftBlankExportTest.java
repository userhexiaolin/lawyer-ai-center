package org.ruoyi.service.legal;

import org.junit.jupiter.api.*;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class DraftBlankExportTest {
    static TemplateFiles.Version version() throws Exception {
        try(var doc=new XWPFDocument();var out=new ByteArrayOutputStream()){
            doc.createParagraph().createRun().setText("合成文书原样空白验证");
            var p=doc.createParagraph();p.createRun().setText("姓名：");p.createRun().setText("  ");p.createRun().setText("。");
            p=doc.createParagraph();p.createRun().setText("本金");var line=p.createRun();line.setText("    ");line.setUnderline(UnderlinePatterns.SINGLE);line.setFontFamily("仿宋");p.createRun().setText("元。");
            doc.createParagraph().createRun().setText("地址：_____。");
            p=doc.createParagraph();p.createRun().setText("律师：{{");p.createRun().setText("律师}}");
            doc.createHeader(HeaderFooterType.DEFAULT).createParagraph().createRun().setText("页眉{{函号}}");
            doc.createFooter(HeaderFooterType.DEFAULT).createParagraph().createRun().setText("页脚{{签名}}");
            doc.createTable(1,1).getRow(0).getCell(0).setText("表格{{电话}}");
            doc.write(out);byte[] bytes=out.toByteArray();
            var blocks=TemplateLayout.inspect(bytes,true).stream().map(b->new TemplateLayout.Block(b.id(),b.text(),true,b.fields())).toList();
            return new TemplateFiles.Version("blank-v1",23L,"合成模板.docx","docx",bytes,bytes,blocks);
        }
    }
    static DocumentExport exporter(TemplateFiles.Version v,DocumentConversion conversion){
        var files=new TemplateFiles(null,conversion){@Override public Version load(String id){return v;}};
        return new DocumentExport(files,new DocumentWord(),conversion);
    }
    static TemplateForm.Composed blank(TemplateFiles.Version v){return TemplateForm.compose(v,new TemplateForm.Input("律师函",23L,v.id(),List.of(),"",""));}
    @Test void emptyFieldsComposeAndSaveWithNativeBlankStylesAndCanonicalMarkers() throws Exception {
        var v=version();var composed=blank(v);var document=TemplateDrafting.read(composed.content());
        assertEquals("partial",document.fillReport().status());assertEquals(0,document.fillReport().filledFields());
        assertTrue(composed.content().contains("{{"));
        String displayed=document.blocks().stream().map(TemplateFields::displayText).collect(java.util.stream.Collectors.joining("\n"));
        assertTrue(displayed.contains("姓名：  。"));assertTrue(displayed.contains("本金____元。"));assertTrue(displayed.contains("地址：_____。"));assertFalse(displayed.contains("{{"));
        assertEquals(composed.content(),exporter(v,mock(DocumentConversion.class)).checkedDraft(composed.content(),composed.input()));
    }
    @Test void allFormatsExportBlankDraftWithoutVisibleMarkersAndKeepWordNativeRuns() throws Exception {
        var v=version();var conversion=mock(DocumentConversion.class);when(conversion.convert(any(),eq("docx"),eq("pdf"))).thenReturn("%PDF-unit-test".getBytes(StandardCharsets.UTF_8));
        var exporter=exporter(v,conversion);var draft=blank(v);
        for(String format:List.of("docx","pdf","md","txt"))assertTrue(exporter.generate("合成文书",draft.content(),format,draft.input()).bytes().length>0);
        for(String format:List.of("md","txt")){String value=new String(exporter.generate("合成文书",draft.content(),format,draft.input()).bytes(),StandardCharsets.UTF_8);assertFalse(value.contains("{{"));assertFalse(value.contains("}}"));}
        var bytes=exporter.generate("合成文书",draft.content(),"docx",draft.input()).bytes();
        try(var before=new XWPFDocument(new ByteArrayInputStream(v.docx()));var after=new XWPFDocument(new ByteArrayInputStream(bytes))){
            assertEquals(before.getParagraphs().get(1).getText(),after.getParagraphs().get(1).getText());
            assertEquals(before.getParagraphs().get(2).getText(),after.getParagraphs().get(2).getText());
            assertEquals(before.getParagraphs().get(2).getRuns().get(1).getCTR().getRPr().xmlText(),after.getParagraphs().get(2).getRuns().get(1).getCTR().getRPr().xmlText());
            assertTrue(after.getParagraphs().get(3).getText().contains("_____"));
            assertTrue(TemplateLayout.inspect(bytes).stream().noneMatch(b->b.text().contains("{{")));
        }
        assertTrue(draft.content().contains("{{"));
    }
    @Test void blankDisplayCanLaterBeFilledAndClearedWithoutLosingTheSlot() throws Exception {
        var v=version();var draft=blank(v);var schema=TemplateForm.schema("合成",v);var name=schema.fields().stream().filter(f->f.label().contains("姓名")).findFirst().orElseThrow();
        var filled=TemplateForm.compose(v,new TemplateForm.Input("律师函",23L,v.id(),List.of(new TemplateForm.Value(name.id(),"合成姓名","","manual")),"",""));
        assertTrue(filled.content().contains("合成姓名"));
        var doc=TemplateDrafting.read(filled.content());var edited=doc.blocks().stream().map(b->new TemplateLayout.Block(b.id(),b.text().replace("合成姓名",""),b.editable())).toList();
        var checked=exporter(v,mock(DocumentConversion.class)).checkedDraft(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(new TemplateDrafting.Document(v.id(),edited)),filled.input());
        assertTrue(checked.contains("{{"+name.label()+"}}"));
    }
    @Test void invalidFixedTextAndWrongVersionRemainRejectedAndConversionFailureIsNotSuccess() throws Exception {
        var v=version();var draft=blank(v);var conversion=mock(DocumentConversion.class);var exporter=exporter(v,conversion);
        assertThrows(DraftingException.class,()->exporter.generate("测试",draft.content().replace("合成文书原样空白验证","篡改固定正文"),"docx",draft.input()));
        assertThrows(DraftingException.class,()->exporter.generate("测试",draft.content().replace("blank-v1","another-v1"),"docx",draft.input()));
        when(conversion.convert(any(),eq("docx"),eq("pdf"))).thenThrow(new DraftingException("合成转换失败",422));
        assertThrows(DraftingException.class,()->exporter.generate("测试",draft.content(),"pdf",draft.input()));
    }
    @Test void legacyDraftGetsNativePresentationWithoutChangingItsRawText() throws Exception {
        var v=version();var draft=blank(v);var document=TemplateDrafting.read(draft.content());
        var legacy=new TemplateDrafting.Document(v.id(),document.blocks().stream().map(b->new TemplateLayout.Block(b.id(),b.text(),b.editable())).toList(),document.calculations(),document.calculationResults(),document.fillReport());
        String raw=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(legacy);
        var restored=TemplateDrafting.read(exporter(v,mock(DocumentConversion.class)).describeDraft(raw,draft.input()));
        assertEquals(legacy.blocks().stream().map(TemplateLayout.Block::text).toList(),restored.blocks().stream().map(TemplateLayout.Block::text).toList());
        assertTrue(restored.blocks().stream().anyMatch(b->b.fields()!=null&&!b.fields().isEmpty()));
    }
}
