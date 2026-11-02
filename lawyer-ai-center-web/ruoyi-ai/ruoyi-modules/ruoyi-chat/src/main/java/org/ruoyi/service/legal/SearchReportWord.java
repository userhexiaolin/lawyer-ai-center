package org.ruoyi.service.legal;

import org.apache.poi.xwpf.usermodel.*;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.domain.vo.legal.SearchReportView.*;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/** Deterministic POI export: snapshots only, without model calls or remote document fetching. */
@Component
public class SearchReportWord {
    public byte[] generate(Report report, List<Item> items, LocalDateTime generatedAt) {
        try (var doc = new XWPFDocument(); var output = new ByteArrayOutputStream()) {
            var section = doc.getDocument().getBody().addNewSectPr();
            var page = section.addNewPgSz(); page.setW(11906); page.setH(16838);
            var margins = section.addNewPgMar(); margins.setTop(1440); margins.setBottom(1440); margins.setLeft(1440); margins.setRight(1440);
            var cover = paragraph(doc, "检索报告", 28, true, null); cover.setAlignment(ParagraphAlignment.CENTER);
            var title = paragraph(doc, report.title(), 20, true, null); title.setAlignment(ParagraphAlignment.CENTER);
            var time = paragraph(doc, "生成时间：" + generatedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "（北京时间）", 11, false, null);
            time.setAlignment(ParagraphAlignment.CENTER);
            doc.createParagraph().setPageBreak(true);
            paragraph(doc, "一、案情背景", 16, true, null);
            paragraph(doc, SearchReportSources.has(report.background()) ? report.background() : "未填写", 11, false, null);
            paragraph(doc, "二、检索条目", 16, true, null);
            int index = 0;
            for (var item : items.stream().sorted(Comparator.comparingInt(Item::sortOrder).thenComparing(Item::id)).toList()) {
                paragraph(doc, ++index + ". " + item.snapshotTitle(), 14, true, null);
                paragraph(doc, "类型：" + type(item.itemType()), 10, false, null);
                if ("law".equals(item.itemType())) {
                    if (SearchReportSources.has(item.lawName())) paragraph(doc, "法规：《" + item.lawName() + "》" + (item.articleNo() == null ? "" : item.articleNo()), 11, true, null);
                    else if (SearchReportSources.has(item.articleNo())) paragraph(doc, "条款号：" + item.articleNo(), 11, true, null);
                    String status = item.lawStatus() == null ? "unknown" : item.lawStatus();
                    String label = switch (status) { case "effective" -> "有效"; case "amended" -> "已修改"; case "repealed" -> "已失效"; default -> "时效待核实"; };
                    paragraph(doc, "法规时效：" + label + "（" + status + "）", 11, true,
                        "effective".equals(status) ? null : "repealed".equals(status) ? "B42318" : "9A4B00");
                }
                if (SearchReportSources.has(item.refId())) paragraph(doc, "来源标识：" + item.refId(), 10, false, null);
                if (SearchReportSources.has(item.sectionKey())) paragraph(doc, "段落位置：" + item.sectionKey(), 10, false, null);
                if (SearchReportSources.has(item.sourceUrl())) paragraph(doc, "来源地址：" + item.sourceUrl(), 10, false, null);
                paragraph(doc, item.snapshotContent(), 11, false, null);
            }
            paragraph(doc, "三、检索结论", 16, true, null);
            paragraph(doc, SearchReportSources.has(report.conclusion()) ? report.conclusion() : "未填写", 11, false, null);
            doc.write(output); return output.toByteArray();
        } catch (Exception e) { throw new ServiceException("报告生成失败：Word 文档生成失败，请检查快照内容后重试", 500); }
    }
    private static String type(String value) {
        return switch (value) { case "case_full" -> "类案全文"; case "case_section" -> "类案片段"; case "law" -> "法规";
            case "viewpoint" -> "观点"; case "chart" -> "图表（文本快照）"; default -> "自定义文本"; };
    }
    private static XWPFParagraph paragraph(XWPFDocument doc, String text, int fontSize, boolean bold, String color) {
        var paragraph = doc.createParagraph(); paragraph.setSpacingAfter(140); paragraph.setSpacingBetween(1.4);
        var run = paragraph.createRun(); run.setFontFamily("微软雅黑"); run.setFontSize(fontSize); run.setBold(bold);
        if (color != null) run.setColor(color);
        String[] lines = (text == null ? "" : text).split("\\R", -1);
        for (int i = 0; i < lines.length; i++) { if (i > 0) run.addBreak(); run.setText(lines[i]); }
        return paragraph;
    }
}
