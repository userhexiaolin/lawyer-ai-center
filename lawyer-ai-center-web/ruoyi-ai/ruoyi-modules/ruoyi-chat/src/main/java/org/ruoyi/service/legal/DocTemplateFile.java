package org.ruoyi.service.legal;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/** Extracts review text. TemplateFiles separately retains the immutable original file. */
public final class DocTemplateFile {
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final int MAX_CHARACTERS = 40000;
    private DocTemplateFile() {}

    public record Preview(String name, String documentType, String content) {}
    public record Extracted(String name, String content) {}

    /** Local immutable-source read for a type change; no AI call and no field reanalysis. */
    public static Extracted extract(String filename, byte[] bytes) {
        return extract(new MultipartFile() {
            public String getName(){return "file";}
            public String getOriginalFilename(){return filename;}
            public String getContentType(){return "application/octet-stream";}
            public boolean isEmpty(){return bytes==null||bytes.length==0;}
            public long getSize(){return bytes==null?0:bytes.length;}
            public byte[] getBytes(){return bytes;}
            public java.io.InputStream getInputStream(){return new java.io.ByteArrayInputStream(bytes);}
            public void transferTo(java.io.File target){throw new UnsupportedOperationException();}
        });
    }

    public static Preview read(MultipartFile file, String type) {
        if (type == null || type.isBlank() || type.length() > 50) throw new DraftingException("请选择有效的模板类型", 400);
        var extracted = extract(file);
        return new Preview(extracted.name(), type, extracted.content());
    }

    public static Extracted extract(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new DraftingException("请选择非空且不超过10MB的模板文件", 400);
        }
        String name = file.getOriginalFilename();
        name = name == null ? "" : name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!Set.of("docx", "pdf", "txt", "md").contains(extension)) {
            throw new DraftingException("仅支持DOCX、PDF、TXT和Markdown模板文件", 400);
        }
        String text;
        try {
            text = switch (extension) {
                case "docx" -> readWord(file);
                case "pdf" -> readPdf(file);
                default -> readText(file);
            };
        } catch (IOException | RuntimeException e) {
            if (e instanceof DraftingException business) throw business;
            throw new DraftingException("无法读取模板文件，请检查文件格式、损坏或加密情况", 400);
        }
        text = text.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (text.isBlank()) {
            throw new DraftingException("未读取到模板正文，请使用含可复制文字的文件，扫描版PDF请先转为文字", 400);
        }
        if (text.length() > MAX_CHARACTERS) {
            throw new DraftingException("模板正文超过40000字，请精简后上传", 400);
        }
        return new Extracted(name.substring(0, Math.min(dot, 100)), text);
    }

    private static String readWord(MultipartFile file) throws IOException {
        try (var input = file.getInputStream(); var document = new XWPFDocument(input);
             var extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private static String readPdf(MultipartFile file) throws IOException {
        try (var document = Loader.loadPDF(file.getBytes())) {
            if (document.getNumberOfPages() > 100) throw new DraftingException("PDF超过100页，请精简后上传", 400);
            return new PDFTextStripper().getText(document);
        }
    }

    private static String readText(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            text = Charset.forName("GB18030").newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        }
        if (text.indexOf('\0') >= 0) throw new DraftingException("模板文件不是有效的文本文件", 400);
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }
}
