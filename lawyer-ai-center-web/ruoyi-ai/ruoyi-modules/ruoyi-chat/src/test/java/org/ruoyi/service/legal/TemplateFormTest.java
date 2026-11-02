package org.ruoyi.service.legal;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class TemplateFormTest {
    private static final String SOURCE = "借款人姓名：合成借款人。贷款总额500000元。合同编号：TEST-123。";
    private TemplateFiles.Version version(String... labels) {
        List<TemplateLayout.Block> blocks = new ArrayList<>();
        blocks.add(new TemplateLayout.Block("title", "合成通知函", false, List.of()));
        for (int i = 0; i < labels.length; i++) {
            String label = labels[i], text = label + "：____";
            var field = new TemplateFields.Field("field-" + i, "p" + i, label.length() + 1, text.length(), "____", label,
                label.equals("金额") ? "money" : label.equals("发函日期") ? "date" : "text", "", "underline", true);
            blocks.add(new TemplateLayout.Block("p" + i, text, true, List.of(field)));
        }
        return new TemplateFiles.Version("version-1", 23L, "合成通知函.docx", "docx", new byte[0], new byte[0], blocks);
    }
    private TemplateForm.Input input(List<TemplateForm.Value> values) {
        return new TemplateForm.Input("律师函", 23L, "version-1", values, SOURCE, "合成合同.docx");
    }
    @Test void templatesExposeTheirOwnExactPositionsIncludingRepeatedLabels() {
        var loan = TemplateForm.schema("贷款通知", version("借款人姓名", "合同编号", "借款人姓名"));
        var property = TemplateForm.schema("物业通知", version("业主姓名", "房屋地址"));
        assertEquals(List.of("借款人姓名", "合同编号", "借款人姓名"), loan.fields().stream().map(TemplateForm.Field::label).toList());
        assertEquals(2, property.fields().size());
        assertEquals(3, new HashSet<>(loan.fields().stream().map(TemplateForm.Field::id).toList()).size());
        assertTrue(loan.fields().get(0).context().contains("____"));
        assertTrue(loan.templateContext().contains("合成通知函"));
        assertTrue(loan.templateContext().contains("{{field-0:借款人姓名}}"));
    }
    @Test void combinesOnlyReviewedFieldsAndLeavesUnknownInformationBlank() {
        var v = version("借款人姓名", "合同编号", "承办律师");
        var result = TemplateForm.compose(v, input(List.of(
            new TemplateForm.Value("field-0", "合成借款人", "借款人姓名：合成借款人。", "extracted"),
            new TemplateForm.Value("field-1", "TEST-123", "合同编号：TEST-123。", "extracted"))));
        var document = TemplateDrafting.read(result.content());
        assertEquals("合成通知函", document.blocks().get(0).text());
        assertEquals("借款人姓名：合成借款人", document.blocks().get(1).text());
        assertEquals("合同编号：TEST-123", document.blocks().get(2).text());
        assertEquals("承办律师：{{承办律师}}", document.blocks().get(3).text());
        assertEquals("partial", document.fillReport().status());
        assertTrue(result.input().contains("---模板填写要素JSON开始---"));
        assertFalse(result.content().contains("待补充"));
        assertEquals("承办律师：____", v.blocks().get(3).text());
    }
    @Test void manuallyProvidedValuesNeedNoInventedContractQuote() {
        var result = TemplateForm.compose(version("承办律师"), input(List.of(new TemplateForm.Value("field-0", "合成人工律师", "", "manual"))));
        assertEquals("承办律师：合成人工律师", TemplateDrafting.read(result.content()).blocks().get(1).text());
        assertEquals("complete", TemplateDrafting.read(result.content()).fillReport().status());
    }
    @Test void inventedValuesAndQuotesUnknownOrDuplicateFieldsAreRejected() {
        var v = version("借款人姓名");
        for (var values : List.of(
            List.of(new TemplateForm.Value("field-0", "不存在的人", "借款人姓名：合成借款人。", "extracted")),
            List.of(new TemplateForm.Value("field-0", "不存在的人", "借款人姓名：不存在的人。", "extracted")),
            List.of(new TemplateForm.Value("unknown", "测试", "", "manual")),
            List.of(new TemplateForm.Value("field-0", "测试", "", "manual"), new TemplateForm.Value("field-0", "测试", "", "manual"))))
            assertThrows(DraftingException.class, () -> TemplateForm.compose(v, input(values)));
    }
    @Test void staleVersionsInvalidMoneyAndImpossibleDatesAreRejected() {
        var values = List.of(new TemplateForm.Value("field-0", "1", "", "manual"));
        assertThrows(DraftingException.class, () -> TemplateForm.compose(version("金额"), new TemplateForm.Input("律师函", 23L, "old-version", values, "", "")));
        assertThrows(DraftingException.class, () -> TemplateForm.compose(version("金额"), input(List.of(new TemplateForm.Value("field-0", "100元", "", "manual")))));
        assertThrows(DraftingException.class, () -> TemplateForm.compose(version("发函日期"), input(List.of(new TemplateForm.Value("field-0", "2026年2月30日", "", "manual")))));
    }
    @Test void explicitFormMoneyIsCombinedWithoutInventingOrRecalculatingOtherFields() {
        var result = TemplateForm.compose(version("金额", "承办律师"), input(List.of(new TemplateForm.Value("field-0", "2500.00", "", "manual"))));
        var doc = TemplateDrafting.read(result.content());
        assertEquals("金额：2500.00", doc.blocks().get(1).text());
        assertEquals("承办律师：{{承办律师}}", doc.blocks().get(2).text());
        assertNull(doc.calculationResults());
        // The same input is used for save/export: manual values survive those checks.
        assertEquals(result.content(), TemplateDrafting.finish(result.content(), result.input(), true));
    }
    @Test void semanticMatchingDoesNotRequireLiteralTemplateRoleCaptionsInTheSource() {
        String quote = "借款人姓名：合成借款人。";
        var result = TemplateForm.compose(version("收件人"), input(List.of(new TemplateForm.Value("field-0", "合成借款人", quote, "extracted", "按当前催款模板文意对应合同借款人，待人工核对"))));
        assertEquals("收件人：合成借款人", TemplateDrafting.read(result.content()).blocks().get(1).text());
        assertTrue(result.input().contains("待人工核对"));
        assertThrows(DraftingException.class, () -> TemplateForm.compose(version("收件人"), input(List.of(new TemplateForm.Value("field-0", "虚构收件人", quote, "extracted")))));
    }
    @Test void numericSubstringsAndDifferentCurrencyScalesAreNotValidSources() {
        var field = new TemplateForm.Field("money", "金额", "money", "元", "金额____元");
        assertFalse(TemplateForm.numericSource(field, "50", "本金500000元"));
        assertFalse(TemplateForm.numericSource(field, "500", "本金500万元"));
        assertFalse(TemplateForm.numericSource(field, "500", "调整金额-500元"));
        assertTrue(TemplateForm.numericSource(field, "1250", "本金1,250.00元"));
        assertTrue(TemplateForm.numericSource(field, "501000.00", "贷款本金50.10万元"));
        assertTrue(TemplateForm.numericSource(field, "90071992547409930000", "贷款本金9007199254740993万元"));
        assertTrue(TemplateForm.grounded(field, "1250.00", "原本金1250元"));
        assertTrue(TemplateForm.numericSource(new TemplateForm.Field("m", "贷款额", "money", "万元", "贷款额____万元"), "50.10", "贷款额501000元"));
    }
    @Test void dateFormattingCanBeNormalizedWithTheSameCalendarDate() {
        var field = new TemplateForm.Field("d", "合同签署时间", "date", "", "签署时间____");
        assertTrue(TemplateForm.grounded(field, "2026-10-09", "合同订立日期：2026年10月9日。"));
        assertFalse(TemplateForm.grounded(field, "2026-11-09", "合同订立日期：2026年10月9日。"));
        assertFalse(TemplateForm.grounded(field, "2026-02-30", "合同订立日期：2026年2月30日。"));
    }
}
