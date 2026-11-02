package org.ruoyi.service.legal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class DraftingCalculationsTest {
    private final ObjectMapper json = new ObjectMapper();
    private final String version = "a862da83-71c0-452d-b747-fd368b75571d";
    private List<TemplateLayout.Block> blocks(String text) { return List.of(new TemplateLayout.Block("p0", "律师函", false), new TemplateLayout.Block("p1", text, true)); }
    private String request(String facts, List<TemplateLayout.Block> blocks) throws Exception {
        return "---起草要素---\n" + json.writeValueAsString(new DraftingFacts.Factors("测试甲乙双方", facts, "依据实际履行明细催告到期本息", ""))
            + "\n\n---版式填写JSON开始---\n" + json.writeValueAsString(new TemplateDrafting.Document(version, blocks)) + "\n---版式填写JSON结束---";
    }
    private DraftingCalculations.Operand operand(String type, String value, String quote) { return new DraftingCalculations.Operand(type, value, quote, null); }
    private DraftingCalculations.Plan plan(String id, int slot, String operation, DraftingCalculations.Operand... inputs) {
        return new DraftingCalculations.Plan(id, "p1", slot, "测试计算项", operation, List.of(inputs), null, null, null, null, null, null);
    }
    private TemplateDrafting.Document finish(String facts, String original, String model, List<DraftingCalculations.Plan> plans) throws Exception {
        return TemplateDrafting.read(TemplateDrafting.finish(json.writeValueAsString(new TemplateDrafting.Document(version, blocks(model), plans, null)), request(facts, blocks(original))));
    }
    private DraftingCalculations.Plan days(String convention, String quote, String from, String to) {
        return new DraftingCalculations.Plan("days", "p1", 0, "逾期天数", "DATE_DAYS", List.of(operand("date", from, "起算日" + from), operand("date", to, "截止日" + to)), convention, quote, null, null, null, null);
    }
    @Test void programAddsExplicitComponentsEvenIfTheModelOmitsOrMiscomputesTheTotal() throws Exception {
        var result = finish("已到期本金10000元，未付正常利息500元。", "本息合计：{{本息合计}}元。", "本息合计：99999元。", null);
        assertEquals("本息合计：10500.00元。", result.blocks().get(1).text());
        assertEquals("calculated", result.calculationResults().get(0).status());
        assertEquals(List.of("已到期本金10000元", "未付正常利息500元"), result.calculationResults().get(0).sources());
        assertTrue(result.calculationResults().get(0).formula().contains("10000.00 + 500.00"));
        var misleading = plan("sum", 0, "SOURCE", operand("money", "10000", "已到期本金10000元"));
        assertEquals("本息合计：10500.00元。", finish("已到期本金10000元，未付正常利息500元。", "本息合计：{{本息合计}}元。", "本息合计：10000元。", List.of(misleading)).blocks().get(1).text());
    }
    @Test void conflictsAndMissingPlansNeverPublishModelArithmetic() throws Exception {
        var conflict = finish("已到期本金10000元，未付正常利息500元，本息合计10600元。", "本息合计：{{本息合计}}元。", "本息合计：99999元。", null);
        assertEquals("本息合计：{{本息合计}}元。", conflict.blocks().get(1).text());
        assertTrue(conflict.calculationResults().get(0).message().contains("不一致"));
        var missing = finish("贷款金额10000元。", "本息合计：{{本息合计}}元。", "本息合计：19999元。", null);
        assertEquals("pending", missing.calculationResults().get(0).status());
        assertTrue(missing.blocks().get(1).text().contains("{{本息合计}}"));
        var coincidental = finish("贷款金额10000元。", "本息合计：{{本息合计}}元。", "本息合计：10000元。", null);
        assertTrue(coincidental.blocks().get(1).text().contains("{{本息合计}}"));
    }
    @Test void decimalArithmeticUnitConversionRoundingAndDependencies() throws Exception {
        var sum = plan("sum", 0, "SUM", operand("money", "10000", "金额1万元"), operand("money", "0.01", "费用0.01元"));
        var product = plan("product", 1, "PRODUCT", new DraftingCalculations.Operand(null, null, null, "sum"), operand("number", "3", "共3期"));
        var result = finish("金额1万元，费用0.01元，共3期。", "{{小计}}；{{总计}}", "错误；错误", List.of(sum, product));
        assertEquals("10000.01；30000.03", result.blocks().get(1).text());
        var divide = plan("divide", 0, "DIVIDE", operand("money", "1", "总价1元"), operand("number", "8", "共8期"));
        assertEquals("0.13", finish("总价1元，共8期。", "{{分项金额}}", "错误", List.of(divide)).blocks().get(1).text());
        var difference = plan("difference", 0, "DIFFERENCE", operand("money", "10", "价款10元"), operand("money", "4", "已付4元"));
        assertEquals("6.00", finish("价款10元，已付4元。", "{{余额}}", "错误", List.of(difference)).blocks().get(1).text());
    }
    @Test void sourceBindingRejectsFabricationNegationExpressionsAndWrongTypes() throws Exception {
        var tests = List.of(
            plan("x", 0, "SUM", operand("money", "999", "本金100元"), operand("money", "1", "费用1元")),
            plan("x", 0, "SUM", operand("money", "100", "其他案件本金100元"), operand("money", "1", "费用1元")),
            plan("x", 0, "SUM", operand("money", "100", "未确认本金100元"), operand("money", "1", "费用1元")),
            plan("x", 0, "SUM", operand("money", "1+99", "本金100元"), operand("money", "1", "费用1元")),
            plan("x", 0, "PRODUCT", operand("money", "100", "本金100元"), operand("money", "1", "费用1元")),
            plan("x", 0, "EXEC", operand("money", "100", "本金100元"), operand("money", "1", "费用1元")));
        for (var test : tests) {
            var result = finish("本金100元，费用1元；未确认本金100元。", "{{计算金额}}", "666", List.of(test));
            assertEquals("pending", result.calculationResults().get(0).status());
            assertEquals("{{计算金额}}", result.blocks().get(1).text());
        }
    }
    @Test void leapYearsDayConventionsAndDateOffsets() throws Exception {
        String facts = "起算日2024-02-28，截止日2024-03-01。不计首日计末日。首尾均计。首尾均不计。期限3天。";
        for (var entry : Map.of("EXCLUDE_START_INCLUDE_END", "2", "INCLUDE_BOTH", "3", "EXCLUDE_BOTH", "1").entrySet()) {
            String quote = entry.getKey().equals("INCLUDE_BOTH") ? "首尾均计" : entry.getKey().equals("EXCLUDE_BOTH") ? "首尾均不计" : "不计首日计末日";
            var result = finish(facts, "{{逾期天数}}", "999", List.of(days(entry.getKey(), quote, "2024-02-28", "2024-03-01")));
            assertEquals(entry.getValue(), result.blocks().get(1).text());
        }
        for (String op : List.of("DATE_ADD", "DATE_SUBTRACT")) {
            var plan = plan("offset", 0, op, operand("date", "2024-02-28", "起算日2024-02-28"), operand("number", "3", "期限3天"));
            assertEquals(op.equals("DATE_ADD") ? "2024年3月2日" : "2024年2月25日", finish(facts, "{{到期日}}", "错误", List.of(plan)).blocks().get(1).text());
        }
        assertEquals("pending", finish(facts, "{{逾期天数}}", "999", List.of(days(null, null, "2024-02-28", "2024-03-01"))).calculationResults().get(0).status());
        assertEquals("pending", finish("起算日2023-02-29，截止日2024-03-01。不计首日计末日。", "{{逾期天数}}", "999", List.of(days("EXCLUDE_START_INCLUDE_END", "不计首日计末日", "2023-02-29", "2024-03-01"))).calculationResults().get(0).status());
    }
    private DraftingCalculations.Plan interest(Integer basis, String basisQuote) {
        return new DraftingCalculations.Plan("interest", "p1", 0, "应付利息", "SIMPLE_INTEREST", List.of(operand("money", "10000", "计息本金10000元"), operand("rate", "6", "年利率6%"), operand("date", "2026-01-01", "起算日2026-01-01"), operand("date", "2026-01-31", "截止日2026-01-31")), "EXCLUDE_START_INCLUDE_END", "不计首日计末日", "year", basis, basisQuote, "按单利计算");
    }
    @Test void simpleInterestRequiresExplicitBasisAndRejectsAmbiguousRules() throws Exception {
        String facts = "计息本金10000元，年利率6%，起算日2026-01-01，截止日2026-01-31。按单利计算，不计首日计末日，每年按360天计息。";
        var result = finish(facts, "利息{{应付利息}}元", "利息999元", List.of(interest(360, "每年按360天计息")));
        assertEquals("利息50.00元", result.blocks().get(1).text());
        assertTrue(result.calculationResults().get(0).formula().contains("30天 ÷ 360"));
        assertEquals("pending", finish(facts, "{{应付利息}}", "999", List.of(interest(null, null))).calculationResults().get(0).status());
        assertEquals("pending", finish(facts, "{{应付利息}}", "999", List.of(interest(365, "每年按360天计息"))).calculationResults().get(0).status());
    }
    @Test void badReferencesDivisionByZeroAndInvalidTargetsAreSafe() throws Exception {
        var loop = plan("loop", 0, "SUM", new DraftingCalculations.Operand(null, null, null, "loop"), operand("money", "1", "费用1元"));
        assertEquals("pending", finish("费用1元", "{{合计}}", "0", List.of(loop)).calculationResults().get(0).status());
        var zero = plan("zero", 0, "DIVIDE", operand("money", "1", "费用1元"), operand("number", "0", "共0期"));
        assertEquals("pending", finish("费用1元，共0期", "{{金额}}", "0", List.of(zero)).calculationResults().get(0).status());
        assertThrows(DraftingException.class, () -> finish("费用1元", "{{合计}}", "0", List.of(loop, loop)));
        assertThrows(DraftingException.class, () -> finish("费用1元", "{{合计}}", "0", List.of(plan("bad", 99, "SUM", operand("money", "1", "费用1元"), operand("money", "1", "费用1元")))));
    }
    @Test void directValuesPendingPersistenceAndDateOrderRemainSafe() throws Exception {
        var direct = plan("source", 0, "SOURCE", operand("money", "120.50", "明确赔偿金额120.50元"));
        var result = finish("明确赔偿金额120.50元。", "{{赔偿金额}}", "999", List.of(direct));
        assertEquals("120.50", result.blocks().get(1).text());
        var foreign = plan("foreign", 0, "SOURCE", operand("money", "100", "港元100元"));
        assertEquals("pending", finish("港元100元", "{{金额}}", "100", List.of(foreign)).calculationResults().get(0).status());
        var reversed = days("EXCLUDE_START_INCLUDE_END", "不计首日计末日", "2024-03-01", "2024-02-28");
        assertEquals("pending", finish("起算日2024-03-01，截止日2024-02-28。不计首日计末日。", "{{逾期天数}}", "999", List.of(reversed)).calculationResults().get(0).status());
        String facts = "贷款金额100元。", original = "{{本息合计}}";
        String pending = json.writeValueAsString(finish(facts, original, original, null));
        assertEquals("pending", TemplateDrafting.read(TemplateDrafting.finish(pending, request(facts, blocks(original)), true)).calculationResults().get(0).status());
        assertNull(finish(facts, "已提供本金100元  ", "已提供本金100元  ", null).calculationResults());
    }
    @Test void saveAndEveryExportRecomputeAndRejectTamperedValuesWithoutChangingLayout() throws Exception {
        String facts = "已到期本金10000元，未付正常利息500元。";
        String original = "本息合计：{{本息合计}}元。";
        var inputBlocks = blocks(original); String request = request(facts, inputBlocks);
        String content = json.writeValueAsString(finish(facts, original, original, null));
        byte[] source;
        try (var doc = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("律师函"); var run = doc.createParagraph().createRun(); run.setFontFamily("仿宋"); run.setFontSize(14); run.setText(original); doc.write(out); source = out.toByteArray();
        }
        var files = mock(TemplateFiles.class); when(files.load(version)).thenReturn(new TemplateFiles.Version(version, 1L, "测试.docx", "docx", source, source, inputBlocks));
        var conversion = mock(DocumentConversion.class); when(conversion.convert(any(), eq("docx"), eq("pdf"))).thenReturn("%PDF-test".getBytes(StandardCharsets.UTF_8));
        var exporter = new DocumentExport(files, new DocumentWord(), conversion);
        assertEquals("10500.00", TemplateDrafting.read(exporter.checkedDraft(content, request)).calculationResults().get(0).value());
        for (String layout : List.of("template", "standard")) for (String format : List.of("docx", "pdf", "md", "txt")) {
            var exported = exporter.generate("测试", content, format, request, layout);
            if (Set.of("md", "txt").contains(format)) { String text = new String(exported.bytes(), StandardCharsets.UTF_8); assertTrue(text.contains("10500.00")); assertFalse(text.contains("calculationResults")); }
            if (format.equals("docx")) try (var word = new XWPFDocument(new ByteArrayInputStream(exported.bytes()))) { assertTrue(word.getParagraphs().stream().anyMatch(p -> p.getText().contains("10500.00"))); }
            assertThrows(DraftingException.class, () -> exporter.generate("测试", content.replace("10500.00元", "99999.00元"), format, request, layout));
        }
        assertThrows(DraftingException.class, () -> exporter.checkedDraft(content.replace("10500.00元", "99999.00元"), request));
        String forged = content.replace("\"value\":\"10500.00\"", "\"value\":\"99999.00\"").replace("\"status\":\"calculated\"", "\"status\":\"confirmed\"");
        assertEquals("10500.00", TemplateDrafting.read(exporter.checkedDraft(forged, request)).calculationResults().get(0).value());
    }
}
