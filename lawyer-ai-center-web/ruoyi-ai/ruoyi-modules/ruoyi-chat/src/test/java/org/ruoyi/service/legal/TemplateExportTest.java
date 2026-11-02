package org.ruoyi.service.legal;

import org.junit.jupiter.api.*;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class TemplateExportTest {
    @Test void oneFieldConfirmationSavesWithoutPreviewAndStillEnforcesReviewAndOwnership()throws Exception{
        byte[] input;
        try(var doc=new XWPFDocument();var out=new ByteArrayOutputStream()){
            doc.createParagraph().createRun().setText("本金____元");doc.write(out);input=out.toByteArray();
        }
        var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var conversion=mock(DocumentConversion.class);
        var blocks=TemplateLayout.inspect(input,true);
        var fields=blocks.get(0).fields();
        var version=new TemplateFiles.Version("version",null,"原件.docx","docx",input,input,blocks);
        var files=new TemplateFiles(jdbc,conversion){@Override public Version load(String id){return version;}};
        when(jdbc.queryForObject(anyString(),eq(Long.class),any(),any())).thenReturn(7L);
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
        try(var login=mockStatic(org.ruoyi.common.satoken.utils.LoginHelper.class)){
            login.when(org.ruoyi.common.satoken.utils.LoginHelper::getTenantId).thenReturn("test-tenant");
            login.when(org.ruoyi.common.satoken.utils.LoginHelper::getUserId).thenReturn(7L);
            assertThrows(DraftingException.class,()->files.bind("version",1L,List.of("p0"),"本金____元",false,fields,false));
            assertThrows(DraftingException.class,()->files.bind("version",1L,List.of("p0"),"本金____元",true,fields,false));
            assertDoesNotThrow(()->files.bind("version",1L,List.of("p0"),"本金____元",false,fields,true));
            verify(jdbc,times(1)).update(anyString(),any(Object[].class));
            assertThrows(DraftingException.class,()->files.bind("version",1L,List.of("p0"),"改写原文____元",false,fields,true));
            when(jdbc.queryForObject(anyString(),eq(Long.class),any(),any())).thenReturn(8L);
            assertThrows(DraftingException.class,()->files.bind("version",1L,List.of("p0"),"本金____元",false,fields,true));
            verify(jdbc,times(1)).update(anyString(),any(Object[].class));
            verifyNoInteractions(conversion);
        }
    }
    @Test void legacySourceAlsoAcceptsSingleContentConfirmationWithoutPdf()throws Exception{
        byte[] input=source();var blocks=TemplateLayout.inspect(input);
        var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);var converter=mock(DocumentConversion.class);
        var version=new TemplateFiles.Version("version",null,"原件.docx","docx",input,input,blocks);
        var files=new TemplateFiles(jdbc,converter){@Override public Version load(String id){return version;}};
        when(jdbc.queryForObject(anyString(),eq(Long.class),any(),any())).thenReturn(7L);
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
        var selected=blocks.stream().map(TemplateLayout.Block::id).toList();
        String content=blocks.stream().map(TemplateLayout.Block::text).reduce((a,b)->a+"\n"+b).orElseThrow();
        try(var login=mockStatic(org.ruoyi.common.satoken.utils.LoginHelper.class)){
            login.when(org.ruoyi.common.satoken.utils.LoginHelper::getTenantId).thenReturn("test-tenant");
            login.when(org.ruoyi.common.satoken.utils.LoginHelper::getUserId).thenReturn(7L);
            assertDoesNotThrow(()->files.bind("version",1L,selected,content,false,null,true));
            assertDoesNotThrow(()->files.bind("version",1L,selected,content,true));
            verifyNoInteractions(converter);
        }
    }
    @Test void uploadOnlyPreservesRawBytesWithoutOpeningOrConvertingTheSource()throws Exception{
        var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);var conversion=mock(DocumentConversion.class);
        var files=new TemplateFiles(jdbc,conversion);byte[] raw={1,2,3,4};
        try(var login=mockStatic(org.ruoyi.common.satoken.utils.LoginHelper.class)){
            login.when(org.ruoyi.common.satoken.utils.LoginHelper::getTenantId).thenReturn("test");login.when(org.ruoyi.common.satoken.utils.LoginHelper::getUserId).thenReturn(7L);
            var result=files.stage(new org.springframework.mock.web.MockMultipartFile("file","原始模板.pdf","application/pdf",raw),"律师函",false);
            assertEquals("uploaded",result.parseStatus());assertEquals("",result.content());assertTrue(result.blocks().isEmpty());assertEquals("原始模板.pdf",result.sourceFilename());
            var args=mockingDetails(jdbc).getInvocations().iterator().next().getArguments();
            assertArrayEquals(raw,(byte[])args[6]);assertTrue(TemplateLayout.state((byte[])args[7]).raw());
            // Container remains readable by older list endpoints, but is never a parsed document.
            assertTrue(TemplateLayout.storedFields((byte[])args[7],List.of()).isEmpty());verifyNoInteractions(conversion);
        }
    }
    @Test void rawDraftRequiresExplicitParsingAndAnalysisCannotOverwriteBoundConfiguration()throws Exception{
        byte[] raw=TemplateLayout.storeState(source(),new TemplateLayout.State(true,false,"",""));
        var v=new TemplateFiles.Version("version",null,"原件.docx","docx",new byte[]{1},raw,List.of());
        var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);var converter=mock(DocumentConversion.class);
        var files=new TemplateFiles(jdbc,converter){@Override public Version load(String id){return v;}};
        when(jdbc.queryForObject(anyString(),eq(Long.class),any(),any())).thenReturn(7L);when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
        try(var login=mockStatic(org.ruoyi.common.satoken.utils.LoginHelper.class)){
            login.when(org.ruoyi.common.satoken.utils.LoginHelper::getTenantId).thenReturn("test");login.when(org.ruoyi.common.satoken.utils.LoginHelper::getUserId).thenReturn(7L);
            assertFalse(TemplateFiles.usable(v));assertThrows(DraftingException.class,()->files.preview("version"));
            assertThrows(DraftingException.class,()->files.bind("version",1L,List.of(),"",false,List.of(),true));
            assertDoesNotThrow(()->files.bind("version",1L,List.of(),"",false,List.of(),false,true));
            assertThrows(DraftingException.class,()->files.requireAnalysisVersion("version"));verifyNoInteractions(converter);
        }
    }
    @Test void savedFieldEditsCloneVersionAndRoundTripWithoutReanalysis()throws Exception{
        byte[] raw;try(var doc=new XWPFDocument();var out=new ByteArrayOutputStream()){doc.createParagraph().createRun().setText("本金____元");doc.write(out);raw=out.toByteArray();}
        var blocks=TemplateLayout.inspect(raw,true);var oldFields=blocks.get(0).fields();
        var selected=blocks.stream().map(b->new TemplateLayout.Block(b.id(),b.text(),true,b.fields())).toList();
        byte[] stored=TemplateLayout.storeState(TemplateLayout.storeFields(raw,selected),new TemplateLayout.State(false,true,"AI","已保存建议"));
        var v=new TemplateFiles.Version("original",3L,"原件.docx","docx",raw,stored,selected);
        var jdbc=mock(org.springframework.jdbc.core.JdbcTemplate.class);var converter=mock(DocumentConversion.class);
        var files=new TemplateFiles(jdbc,converter){@Override public Version load(String id){return v;}@Override public Version current(Long id){return v;}};
        when(jdbc.queryForObject(anyString(),eq(Long.class),any(),any())).thenReturn(7L);when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1);
        var f=oldFields.get(0);var edited=List.of(new TemplateFields.Field(f.id(),f.blockId(),f.start(),f.end(),f.original(),"人工修正本金",f.kind(),f.unit(),f.presentation(),f.enabled()));
        try(var login=mockStatic(org.ruoyi.common.satoken.utils.LoginHelper.class)){
            login.when(org.ruoyi.common.satoken.utils.LoginHelper::getTenantId).thenReturn("test");login.when(org.ruoyi.common.satoken.utils.LoginHelper::getUserId).thenReturn(7L);
            files.bind("original",3L,List.of("p0"),"本金____元",false,edited,true);
            var updates=mockingDetails(jdbc).getInvocations().stream().filter(i->i.getMethod().getName().equals("update")).toList();assertEquals(2,updates.size());
            byte[] next=(byte[])updates.get(1).getArguments()[3];assertTrue(TemplateLayout.state(next).confirmed());assertEquals("人工修正本金",TemplateLayout.storedFields(next,List.of()).get(0).fields().get(0).label());
            assertEquals(oldFields.get(0).label(),TemplateLayout.storedFields(stored,List.of()).get(0).fields().get(0).label());
            assertThrows(DraftingException.class,()->files.requireAnalysisVersion("original"));verifyNoInteractions(converter);
        }
    }
    @Test void omittingSourceVersionCannotEnableAnUnreviewedStoredTemplate(){
        var row=new org.ruoyi.domain.entity.legal.LegalDocTemplate();row.setId(3L);row.setSourceVersion("bound");row.setContent("律师函");row.setFieldsConfirmed(false);
        var mapper=mock(org.ruoyi.mapper.legal.LegalDocTemplateMapper.class);var types=mock(DocTemplateTypeService.class);
        var service=new DocTemplateService(mapper,types){@Override public org.ruoyi.domain.entity.legal.LegalDocTemplate get(Long id){return row;}};
        var error=assertThrows(DraftingException.class,()->service.update(3L,new DocTemplateService.Input("模板","律师函","律师函",0,"0","")));
        assertEquals("请先解析并核对填写字段后启用模板",error.getMessage());verifyNoInteractions(mapper);
    }
    static byte[] source() throws Exception {
        try(var doc=new XWPFDocument();var out=new ByteArrayOutputStream()){
            var sect=doc.getDocument().getBody().addNewSectPr();var size=sect.addNewPgSz();size.setW(11906);size.setH(16838);var margins=sect.addNewPgMar();margins.setLeft(1800);margins.setRight(1500);margins.setTop(1400);margins.setBottom(1400);
            var heading=doc.createParagraph();heading.setAlignment(ParagraphAlignment.CENTER);heading.setSpacingAfter(320);var title=heading.createRun();title.setText("律 师 函");title.setFontFamily("黑体");title.setFontSize(22);title.setBold(true);
            var recipient=doc.createParagraph();recipient.setSpacingBetween(1.8);var prefix=recipient.createRun();prefix.setText("致：");prefix.setFontFamily("黑体");prefix.setBold(true);prefix.setFontSize(16);
            var target=recipient.createRun();target.setText("{{收件人}}");target.setFontFamily("仿宋");target.setFontSize(14);
            var body=doc.createParagraph();body.setIndentationFirstLine(560);body.setSpacingBetween(1.75);body.setSpacingAfter(160);var run=body.createRun();run.setText("{{正文}}");run.setFontFamily("仿宋");run.setFontSize(14);
            var table=doc.createTable(2,2);table.getRow(0).getCell(0).setText("事项");table.getRow(0).getCell(1).setText("期限");table.getRow(1).getCell(0).setText("履行要求");table.getRow(1).getCell(1).setText("{{期限}}");
            var header=doc.createHeader(HeaderFooterType.DEFAULT).createParagraph();header.setAlignment(ParagraphAlignment.RIGHT);header.createRun().setText("测试律师事务所 · 固定页眉");
            doc.createFooter(HeaderFooterType.DEFAULT).createParagraph().createRun().setText("测试文件 · 固定页脚");
            doc.write(out);return out.toByteArray();
        }
    }
    static List<TemplateLayout.Block> completed(byte[] source){return TemplateLayout.inspect(source).stream().map(b->new TemplateLayout.Block(b.id(),b.text().replace("{{收件人}}","乙测试公司").replace("{{正文}}","本函依据委托人提供的材料起草。请贵方于约定期限内履行合同项下付款义务。\n具体履行金额及依据以双方合同和已核实的材料为准。").replace("{{期限}}","收到本函之日起七日内"),b.text().contains("{{"))).toList();}
    @Test void originalFontsRunStylesParagraphsTablesHeadersAndMarginsSurviveEdits() throws Exception {
        byte[] input=source(),output=TemplateLayout.fill(input,completed(input));
        try(var before=new XWPFDocument(new ByteArrayInputStream(input));var after=new XWPFDocument(new ByteArrayInputStream(output))){
            assertEquals(before.getDocument().getBody().getSectPr().xmlText(),after.getDocument().getBody().getSectPr().xmlText());
            assertEquals(before.getParagraphs().size()+1,after.getParagraphs().size());
            for(int p=0;p<before.getParagraphs().size();p++){
                var old=before.getParagraphs().get(p);var now=after.getParagraphs().get(p);
                assertEquals(old.getCTP().getPPr().xmlText(),now.getCTP().getPPr().xmlText());
                for(int r=0;r<old.getRuns().size();r++)assertEquals(old.getRuns().get(r).getCTR().getRPr().xmlText(),now.getRuns().get(r).getCTR().getRPr().xmlText());
            }
            assertEquals("黑体",after.getParagraphs().get(1).getRuns().get(0).getFontFamily());
            assertEquals("仿宋",after.getParagraphs().get(1).getRuns().get(1).getFontFamily());
            assertEquals("乙测试公司",after.getParagraphs().get(1).getRuns().get(1).text());
            assertEquals(before.getParagraphs().get(2).getCTP().getPPr().xmlText(),after.getParagraphs().get(3).getCTP().getPPr().xmlText());
            assertEquals(before.getTables().get(0).getCTTbl().getTblPr().xmlText(),after.getTables().get(0).getCTTbl().getTblPr().xmlText());
            assertEquals("收到本函之日起七日内",after.getTables().get(0).getRow(1).getCell(1).getText());
            assertEquals(before.getHeaderList().get(0).getText(),after.getHeaderList().get(0).getText());assertEquals(before.getFooterList().get(0).getText(),after.getFooterList().get(0).getText());
        }
    }
    @Test void fixedTemplateTextCannotBeRewrittenByTheModelAndAllFieldsAreRequired() throws Exception {
        byte[] input=source();String id="a862da83-71c0-452d-b747-fd368b75571d";
        var blocks=TemplateLayout.inspect(input).stream().map(b->new TemplateLayout.Block(b.id(),b.text(),b.text().contains("{{"))).toList();
        var version=new TemplateFiles.Version(id,1L,"测试.docx","docx",input,input,blocks);String request=TemplateDrafting.prompt(version);
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        var changed=completed(input).stream().map(b->new TemplateLayout.Block(b.id(),b.editable()?b.text():"模型试图改写固定文字",b.editable())).toList();
        String result=TemplateDrafting.finish(json.writeValueAsString(new TemplateDrafting.Document(id,changed)),request);
        assertTrue(TemplateDrafting.plain(TemplateDrafting.read(result)).contains("律 师 函"));assertFalse(result.contains("模型试图"));
        assertThrows(DraftingException.class,()->TemplateDrafting.finish("正文",request));
        assertThrows(DraftingException.class,()->TemplateDrafting.finish(json.writeValueAsString(new TemplateDrafting.Document(id,List.of())),request));
    }
    @Test void mixedFormattingRequiresAnExplicitUniformPlaceholder()throws Exception{
        byte[] input=source();assertDoesNotThrow(()->TemplateLayout.validateEditable(input,Set.of("p1","p2")));
        try(var doc=new XWPFDocument(new ByteArrayInputStream(input));var out=new ByteArrayOutputStream()){
            doc.getParagraphs().get(1).getRuns().get(1).setText("示例公司",0);doc.write(out);
            assertDoesNotThrow(()->TemplateLayout.validateEditable(out.toByteArray(),Set.of("p1")));
            assertThrows(DraftingException.class,()->TemplateLayout.validateReplacement("致：示例公司","致：另一公司"));
        }
    }
    @Test void legacyDraftNeedsExplicitStandardLayoutAndCannotBypassTemplateBinding()throws Exception{
        var conversion=new DocumentConversion();
        try{
            var exporter=new DocumentExport(new TemplateFiles(null,conversion),new DocumentWord(),conversion);
            String request="---律所模板开始---\n律师函模板\n---律所模板结束---";
            assertThrows(DraftingException.class,()->exporter.generate("律师函","当前定稿","docx",request));
            var standard=exporter.generate("律师函","当前定稿","docx",request,"standard");
            try(var doc=new XWPFDocument(new ByteArrayInputStream(standard.bytes()))){
                assertTrue(doc.getParagraphs().stream().anyMatch(p->p.getText().contains("当前定稿")));
            }
            assertThrows(DraftingException.class,()->exporter.generate("律师函","当前定稿","docx",request,"invalid"));
            String structured=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(new TemplateDrafting.Document("version",List.of(new TemplateLayout.Block("p1","当前定稿",true))));
            assertThrows(DraftingException.class,()->exporter.generate("律师函",structured,"docx",request,"standard"));
        }finally{conversion.close();}
    }
    @Test void textTemplateImportsKeepSlotsButFinishedExportsRejectThem(){
        var files=new TemplateFiles(mock(org.springframework.jdbc.core.JdbcTemplate.class),mock(DocumentConversion.class));
        try(var login=mockStatic(org.ruoyi.common.satoken.utils.LoginHelper.class)){
            login.when(org.ruoyi.common.satoken.utils.LoginHelper::getTenantId).thenReturn("test-tenant");login.when(org.ruoyi.common.satoken.utils.LoginHelper::getUserId).thenReturn(7L);
            for(String extension:List.of("txt","md")){
                var staged=files.stage(new org.springframework.mock.web.MockMultipartFile("file","律师函."+extension,"text/plain","# 律师函\n{{正文}}".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"律师函");
                assertTrue(staged.content().contains("{{正文}}"));assertTrue(staged.blocks().stream().anyMatch(b->b.text().equals("{{正文}}")));assertEquals(extension,staged.sourceFormat());
            }
        }
        assertThrows(org.ruoyi.common.core.exception.ServiceException.class,()->new DocumentWord().generate("律师函","{{正文}}"));
    }
    @Test void allFormatsUseTheSameEditedContentAndRejectWrongTemplateBinding()throws Exception{
        byte[] input=source();String id="a862da83-71c0-452d-b747-fd368b75571d";
        var version=new TemplateFiles.Version(id,1L,"测试.docx","docx",input,input,completed(input).stream().map(b->new TemplateLayout.Block(b.id(),TemplateLayout.inspect(input).stream().filter(x->x.id().equals(b.id())).findFirst().orElseThrow().text(),b.editable())).toList());
        var files=mock(TemplateFiles.class);when(files.load(id)).thenReturn(version);var converter=mock(DocumentConversion.class);when(converter.convert(any(),eq("docx"),eq("pdf"))).thenReturn("%PDF-test".getBytes());
        var service=new DocumentExport(files,new DocumentWord(),converter);String content=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(new TemplateDrafting.Document(id,completed(input))),request=TemplateDrafting.prompt(version);
        assertEquals("docx",service.generate("律师函",content,"docx",request).extension());assertEquals("application/pdf",service.generate("律师函",content,"pdf",request).mime());
        for(String format:List.of("md","txt")){String text=new String(service.generate("律师函",content,format,request).bytes(),java.nio.charset.StandardCharsets.UTF_8);assertTrue(text.contains("乙测试公司"));assertFalse(text.contains("documentTemplate"));assertFalse(text.contains("{{"));}
        String markdown=new String(service.generate("律师函",content,"md",request).bytes(),java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(markdown.contains("**律 师 函**"));assertTrue(markdown.contains("| 事项 | 期限 |"));assertTrue(markdown.contains("| --- | --- |"));assertTrue(markdown.contains("固定页眉"));
        try(var doc=new XWPFDocument();var out=new ByteArrayOutputStream()){
            var heading=doc.createParagraph();heading.setStyle("Heading2");heading.createRun().setText("履行要求");doc.write(out);
            assertEquals("## 履行要求",TemplateLayout.markdown(out.toByteArray()));
        }
        assertThrows(DraftingException.class,()->service.generate("律师函",content,"docx",""));assertThrows(DraftingException.class,()->service.generate("律师函","旧稿","docx","---律所模板开始---"));
        assertThrows(DraftingException.class,()->service.generate("律师函",content,"exe",request));
    }
    @Test void nativeConversionAndRenderedArtifacts()throws Exception{
        if(!Boolean.getBoolean("legal.export.nativeCheck"))return;
        Path root=Path.of(System.getProperty("legal.export.evidence"));Files.createDirectories(root);
        byte[] input=source(),output=TemplateLayout.fill(input,completed(input));Files.write(root.resolve("template.docx"),input);Files.write(root.resolve("draft.docx"),output);
        var conversion=new DocumentConversion();byte[] pdf;
        try{pdf=conversion.convert(output,"docx","pdf");long started=System.nanoTime();byte[] repeated=conversion.convert(output,"docx","pdf");assertTrue(repeated.length>100);System.out.println("Repeated PDF export elapsedMs="+(System.nanoTime()-started)/1000000);}finally{conversion.close();}
        Files.write(root.resolve("draft.pdf"),pdf);
        try(var document=Loader.loadPDF(pdf)){assertTrue(document.getNumberOfPages()>0);var renderer=new PDFRenderer(document);for(int i=0;i<document.getNumberOfPages();i++)ImageIO.write(renderer.renderImageWithDPI(i,120),"PNG",root.resolve("page-"+(i+1)+".png").toFile());}
    }
}
