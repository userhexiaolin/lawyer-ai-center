package org.ruoyi.service.legal;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class DocTemplateFileTest {
    private static final String TYPE = "民事起诉状";
    private MockMultipartFile text(String name, String content) {
        return new MockMultipartFile("file", name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test void importsMarkdownAndNormalizesBomAndLineEndings() {
        var preview = DocTemplateFile.read(text("C:\\fakepath\\起诉状.MD", "\uFEFF# {{原告}}\r\n\r\n{{诉讼请求}}\r\n"), TYPE);
        assertEquals("起诉状", preview.name());
        assertEquals(TYPE, preview.documentType());
        assertEquals("# {{原告}}\n\n{{诉讼请求}}", preview.content());
    }

    @Test void importsChineseWindowsText() {
        var file = new MockMultipartFile("file", "律师函.txt", "text/plain", "律师函：{{收件人}}".getBytes(Charset.forName("GB18030")));
        assertEquals("律师函：{{收件人}}", DocTemplateFile.read(file, "律师函").content());
    }

    @Test void importsWordParagraphsAndTablesWithoutLosingPlaceholders() throws Exception {
        try (var document = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("民事起诉状 {{原告}}");
            document.createTable(1, 1).getRow(0).getCell(0).setText("{{诉讼请求}}");
            document.write(out);
            var result = DocTemplateFile.read(new MockMultipartFile("file", "起诉状.docx", "application/octet-stream", out.toByteArray()), TYPE);
            assertTrue(result.content().contains("民事起诉状 {{原告}}"));
            assertTrue(result.content().contains("{{诉讼请求}}"));
        }
    }

    @Test void importsTextPdf() throws Exception {
        try (var document = new PDDocument(); var out = new ByteArrayOutputStream()) {
            var page = new PDPage();
            document.addPage(page);
            try (var stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(40, 700);
                stream.showText("Template {{party}}");
                stream.endText();
            }
            document.save(out);
            assertEquals("Template {{party}}", DocTemplateFile.read(new MockMultipartFile("file", "template.pdf", "application/pdf", out.toByteArray()), TYPE).content());
        }
    }

    @Test void rejectsInvalidTypeUnsupportedEmptyAndOversizedFiles() {
        assertThrows(DraftingException.class, () -> DocTemplateFile.read(text("a.txt", "正文"), " "));
        assertThrows(DraftingException.class, () -> DocTemplateFile.read(text("a.exe", "正文"), TYPE));
        assertThrows(DraftingException.class, () -> DocTemplateFile.read(text("a.txt", ""), TYPE));
        var large = new MockMultipartFile("file", "large.txt", "text/plain", new byte[]{1}) {
            @Override public long getSize() { return 10 * 1024 * 1024 + 1; }
        };
        assertThrows(DraftingException.class, () -> DocTemplateFile.read(large, TYPE));
    }

    @Test void rejectsBlankLongBinaryAndCorruptContentRatherThanTruncating() {
        assertThrows(DraftingException.class, () -> DocTemplateFile.read(text("a.md", " \n "), TYPE));
        assertEquals(40000, DocTemplateFile.read(text("a.txt", "字".repeat(40000)), TYPE).content().length());
        assertThrows(DraftingException.class, () -> DocTemplateFile.read(text("a.txt", "字".repeat(40001)), TYPE));
        assertThrows(DraftingException.class, () -> DocTemplateFile.read(text("a.txt", "binary\0file"), TYPE));
        assertThrows(DraftingException.class, () -> DocTemplateFile.read(text("a.docx", "并非Word文件"), TYPE));
        assertThrows(DraftingException.class, () -> DocTemplateFile.read(text("a.pdf", "并非PDF文件"), TYPE));
    }

    @Test void rejectsPdfWithoutText() throws Exception {
        try (var document = new PDDocument(); var out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);
            var error = assertThrows(DraftingException.class, () -> DocTemplateFile.read(new MockMultipartFile("file", "scan.pdf", "application/pdf", out.toByteArray()), TYPE));
            assertTrue(error.getMessage().contains("未读取到模板正文"));
        }
    }
}
