package org.ruoyi.service.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.xwpf.usermodel.*;
import org.ruoyi.common.core.exception.ServiceException;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Word report uses the same raw result as the result panel. POI escapes document text. */
public final class ContractReviewReport {
    private ContractReviewReport() {}
    static JsonNode parse(String content) {
        if (content == null || content.length() > 300000) throw new ServiceException("审查内容无效或过长");
        var mapper = new ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder()
            .enable(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_TRAILING_COMMA).build());
        List<JsonNode> results = new ArrayList<>();
        int depth = 0, start = -1; boolean quoted = false, escaped = false;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (depth == 0) { if (c == '{') { start = i; depth = 1; quoted = false; escaped = false; } continue; }
            if (quoted) { if (escaped) escaped = false; else if (c == '\\') escaped = true; else if (c == '"') quoted = false; continue; }
            if (c == '"') quoted = true;
            else if (c == '{') depth++;
            else if (c == '}' && --depth == 0) {
                try { var node = mapper.readTree(content.substring(start, i + 1)); if (node.isObject()) results.add(node); }
                catch (Exception ignored) { }
            }
        }
        if (depth != 0 || results.size() != 1) throw new ServiceException("结构化解析失败，暂不能生成报告");
        var result = (ObjectNode) results.get(0);
        boolean modern = result.has("issues");
        var entries = result.path(modern ? "issues" : "risks");
        if (text(result.path("summary")).isBlank() || !entries.isArray()) throw new ServiceException("审查结果缺少摘要或风险列表");
        var issues = mapper.createArrayNode();
        for (var risk : entries) {
            if (!risk.isObject()) throw new ServiceException("风险点格式无效");
            var issue = ((ObjectNode) risk).deepCopy();
            String title = text(modern ? alias(risk,"clauseTitle","clause_title") : risk.path("title"));
            if (title.isBlank()) throw new ServiceException("风险点格式无效");
            issue.put("clauseTitle", title);
            issue.put("riskLevel", text(modern ? alias(risk,"riskLevel","risk_level") : risk.path("level")));
            issue.put("quote", text(risk.path(modern ? "quote" : "clause")));
            issue.put("problem", text(risk.path(modern ? "problem" : "analysis")));
            issue.put("suggestion", text(risk.path("suggestion")));
            if (!issue.has("id")) issue.put("id", String.valueOf(issues.size()+1));
            var location = risk.path("location").isObject() ? ((ObjectNode) risk.path("location")).deepCopy() : mapper.createObjectNode();
            if (!modern && !text(risk.path("position")).isBlank()) location.put("positionText",text(risk.path("position")));
            if (!location.has("positionText") && location.has("position_text")) location.set("positionText",location.path("position_text"));
            issue.set("location",location);
            var lawRefs=mapper.createArrayNode(); var caseRefs=mapper.createArrayNode();
            var laws = modern ? alias(risk,"lawRefs","law_refs") : risk.path("legalBasis");
            if (laws.isTextual()) lawRefs.add(laws);
            else if (laws.isArray()) for (var ref : laws) { if ("case".equals(text(ref.path("type")))) caseRefs.add(ref); else lawRefs.add(ref); }
            var cases=alias(risk,"caseRefs","case_refs");
            if(cases.isArray()) cases.forEach(caseRefs::add);
            issue.set("lawRefs",lawRefs); issue.set("caseRefs",caseRefs);
            issues.add(issue);
        }
        result.set("issues",issues);
        result.put("contractType",text(alias(result,"contractType","contract_type")));
        if (!result.has("riskLevel")) {
            String overall="unknown";
            for(var issue:issues) { String l=text(issue.path("riskLevel")); if(rank(l)<rank(overall)) overall=l; }
            result.put("riskLevel",overall);
        }
        return result;
    }
    static JsonNode alias(JsonNode node,String key,String alternate) { return node.has(key) ? node.path(key) : node.path(alternate); }
    static int rank(String level) { return switch(level.toLowerCase(Locale.ROOT)) { case "high" -> 0; case "medium" -> 1; case "low" -> 2; default -> 3; }; }
    static String position(JsonNode location) {
        List<String> parts=new ArrayList<>();
        if(!text(location.path("positionText")).isBlank()) parts.add(text(location.path("positionText")));
        if(location.path("page").canConvertToInt() && location.path("page").asInt()>0) parts.add("第"+location.path("page").asInt()+"页");
        if(location.path("paragraph").canConvertToInt() && location.path("paragraph").asInt()>0) parts.add("第"+location.path("paragraph").asInt()+"段");
        return String.join(" / ",parts);
    }
    static String text(JsonNode node) { return node.isTextual() ? node.asText().strip() : ""; }
    static String list(JsonNode value) {
        if (value.isTextual()) return text(value);
        if (!value.isArray()) return "";
        List<String> entries = new ArrayList<>();
        value.forEach(v -> {
            String entry = text(v);
            if (v.isObject()) {
                String caseNo=text(alias(v,"caseNo","case_no"));
                String lawName=text(alias(v,"lawName", "name"));
                String articleNo=text(alias(v,"articleNo", "article"));
                if (!caseNo.isBlank()) entry = caseNo;
                else if (!lawName.isBlank() && !articleNo.isBlank()) {
                    String article = articleNo;
                    if (!article.startsWith("第")) article = "第" + article + (article.endsWith("条") ? "" : "条");
                    entry = "《" + lawName.replace("《", "").replace("》", "") + "》" + article;
                    String status = text(v.path("status"));
                    if (!"effective".equals(status)) entry += "（" + ("repealed".equals(status) ? "已失效" : "amended".equals(status) ? "已修改" : "时效待核实") + "）";
                }
                String url=text(alias(v,"sourceUrl","url"));
                if (!entry.isBlank() && !url.isBlank()) entry += " " + url;
            }
            if (!entry.isBlank()) entries.add(entry);
        });
        return String.join("；", entries);
    }
    static String level(JsonNode value) {
        return switch (text(value).toLowerCase(Locale.ROOT)) { case "high" -> "高风险"; case "medium" -> "中风险"; case "low" -> "低风险"; default -> "风险待确认"; };
    }
    private static void paragraph(XWPFDocument doc, String text, boolean heading) {
        var p = doc.createParagraph(); p.setSpacingAfter(140);
        var run = p.createRun(); run.setFontFamily("微软雅黑"); run.setFontSize(heading ? 15 : 11); run.setBold(heading);
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) { if (i > 0) run.addBreak(); run.setText(lines[i]); }
    }
    public static byte[] generate(String content) {
        JsonNode result = parse(content);
        try (var doc = new XWPFDocument(); var output = new ByteArrayOutputStream()) {
            // Landscape A4 keeps the detailed risk table legible.
            var size = doc.getDocument().getBody().addNewSectPr().addNewPgSz();
            size.setW(16838); size.setH(11906);
            paragraph(doc, "合同审查报告", true);
            String date = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
            var basic = result.path("basicInfo");
            paragraph(doc, "合同类型：" + text(result.path("contractType")) + "\n合同名称：" + text(basic.path("title")) + "\n当事人：" + list(basic.path("parties")) + "\n签署日期：" + text(basic.path("signDate")) + "\n审查日期：" + date, false);
            paragraph(doc, "整体风险：" + level(result.path("riskLevel")), true);
            paragraph(doc, text(result.path("summary")), false);
            paragraph(doc, "风险点明细", true);
            var table = doc.createTable(1, 8); table.setWidth("100%");
            String[] headers = {"序号", "风险等级", "位置", "风险点 / 问题描述", "原文摘录", "修改建议", "引用法条（法规名+条号）", "引用类案"};
            for (int i = 0; i < headers.length; i++) table.getRow(0).getCell(i).setText(headers[i]);
            List<JsonNode> risks = new ArrayList<>(); result.path("issues").forEach(risks::add);
            risks.sort(Comparator.comparingInt(r -> rank(text(r.path("riskLevel")))));
            for (int index = 0; index < risks.size(); index++) {
                var risk = risks.get(index); var row = table.createRow();
                String[] cells = {String.valueOf(index + 1), level(risk.path("riskLevel")), position(risk.path("location")), text(risk.path("clauseTitle")) + "\n" + text(risk.path("problem")), text(risk.path("quote")), text(risk.path("suggestion")), list(risk.path("lawRefs")), list(risk.path("caseRefs"))};
                for (int i = 0; i < cells.length; i++) row.getCell(i).setText(cells[i]);
            }
            for (var row : table.getRows()) for (var cell : row.getTableCells()) for (var p : cell.getParagraphs()) for (var run : p.getRuns()) { run.setFontFamily("微软雅黑"); run.setFontSize(10); }
            paragraph(doc, "缺失条款提示", true); paragraph(doc, list(result.path("missingClauses")), false);
            paragraph(doc, "综合建议", true); paragraph(doc, text(result.path("overallSuggestion")), false);
            paragraph(doc, "审查日期：" + date + "\n本报告由与律行 AI 辅助生成，仅供参考", false);
            doc.write(output); return output.toByteArray();
        } catch (java.io.IOException e) { throw new ServiceException("报告生成失败，请重试"); }
    }
}
