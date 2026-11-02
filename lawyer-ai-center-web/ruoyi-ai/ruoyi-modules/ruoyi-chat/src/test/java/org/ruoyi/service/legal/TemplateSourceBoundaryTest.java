package org.ruoyi.service.legal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class TemplateSourceBoundaryTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private static String request(String facts, String purpose) throws Exception {
        return "---起草要素---\n" + JSON.writeValueAsString(new DraftingFacts.Factors("甲方：合成甲", facts, purpose, ""));
    }

    @Test void emptyLabelsNeverConsumeFollowingLinesOrAnotherSourceSection() throws Exception {
        for (String separator : List.of("\n", "\r\n", "\n　\t")) {
            String labels = String.join(separator, "委托人：", "发函方：", "收件人：", "承办律师：", "合同编号：");
            var known = TemplateSourceValues.read(request("合同未指定本次发函信息。", labels));
            assertFalse(known.containsKey("client"));
            assertFalse(known.containsKey("recipient"));
            assertFalse(known.containsKey("lawyer"));
            assertFalse(known.containsKey("contractNo"));
        }
        var acrossSections = TemplateSourceValues.read(request("委托人：", "发函方：\n收件人："));
        assertFalse(acrossSections.containsKey("client"));
    }

    @Test void adjacentLabelsAreNotAnEntityAndDoNotPolluteACompleteName() throws Exception {
        var empty = TemplateSourceValues.read(request("合同内容。", "委托人：发函方：收件人："));
        assertFalse(empty.containsKey("client"));
        var complete = TemplateSourceValues.read(request("合同内容。", "委托人：合成商业银行庐山支行，收件人：合成乙。"));
        assertEquals("合成商业银行庐山支行", complete.get("client"));
        assertEquals("合成乙", complete.get("recipient"));
    }

    @Test void missingMarkersPlaceholdersAndLabelOnlyValuesAreNotFacts() throws Exception {
        for (String value : List.of("未明确", "未确认", "待核实", "待确定", "【待补充】", "{{发函方}}", "___", "发函方：", "发函方", "n/a", "TBD")) {
            assertFalse(TemplateSourceValues.meaningfulValue(value), value);
            assertFalse(TemplateSourceValues.read(request("合同内容。", "发函方：" + value)).containsKey("client"), value);
        }
        assertFalse(TemplateSourceValues.meaningfulValue(null));
        assertTrue(TemplateSourceValues.meaningfulValue("合成商业银行庐山支行"));
        assertTrue(TemplateSourceValues.meaningfulValue("“合成商业银行庐山支行”"));
        assertTrue(TemplateSourceValues.meaningfulValue("2026年10月28日"));
        assertTrue(TemplateSourceValues.meaningfulValue("0.00"));
    }

    @Test void completeEntitiesAndSourceQuotesRemainIntact() throws Exception {
        String facts = "委托人及发函方：合成商业银行股份有限公司庐山支行。收件人：合成乙。\n"
            + "承办律师姓名：合成丙；律师联系电话：000-12345678。\n合同编号：TEST-2026-001";
        var known = TemplateSourceValues.read(request(facts, "委托人：\n发函方：\n收件人："));
        assertEquals("合成商业银行股份有限公司庐山支行", known.get("client"));
        assertEquals("合成乙", known.get("recipient"));
        assertEquals("合成丙", known.get("lawyer"));
        assertEquals("000-12345678", known.get("lawyerPhone"));
        assertEquals("TEST-2026-001", known.get("contractNo"));
        assertEquals("“合成商业银行”", TemplateSourceValues.read(request("发函方：“合成商业银行”。", "")).get("client"));
    }

    @Test void amountAndDateSemanticsKeepExplicitLabelsAndWrappedDates() throws Exception {
        String facts = "首期逾期起算日：2026年10月2日。核算截止日：2026年10月27\n日。拟定发函日期：2026年10月28日。"
            + "已到期逾期本金合计5,000.00元。未付正常利息合计250.00元。本息合计5250.00元。"
            + "逾期罚息合计20.00元。本次催收总额5270.00元。催告期限：7个自然日，截止2026年11月4日。";
        var known = TemplateSourceValues.read(request(facts, "委托人：\n发函方："));
        assertEquals("2026年10月2日", known.get("overdueDate"));
        assertEquals("2026年10月27日", known.get("cutoffDate"));
        assertEquals("2026年10月28日", known.get("letterDate"));
        assertEquals("2026年11月4日", known.get("demandDate"));
        assertEquals("5000.00", known.get("principal"));
        assertEquals("250.00", known.get("interest"));
        assertEquals("5250.00", known.get("total"));
        assertEquals("20.00", known.get("penalty"));
        assertEquals("5270.00", known.get("claimTotal"));
    }

    @Test void ambiguousCompleteEntitiesRemainUnresolved() throws Exception {
        var known = TemplateSourceValues.read(request("发函方：合成甲银行。发函方：合成乙银行。", ""));
        assertFalse(known.containsKey("client"));
    }

    @Test void invalidAutomaticCandidateCannotOverwriteGroundedModelValue() throws Exception {
        String source = "本所接受合成商业银行委托办理本次催告。";
        String input = withTemplate(request(source, "委托人：\n发函方：\n收件人："));
        String response = JSON.writeValueAsString(Map.of("documentTemplate", "source-version", "fieldValues", List.of(
            Map.of("fieldId", "p0-client", "value", "合成商业银行", "quote", source))));
        var result = TemplateDrafting.read(TemplateDrafting.finish(response, input));
        assertEquals("接受合成商业银行委托。", result.blocks().get(0).text());
    }

    @Test void omittedModelFieldRetainsPlaceholderInsteadOfInsertingAnotherLabel() throws Exception {
        String input = withTemplate(request("合同未明确本次委托人。", "委托人：\n发函方：\n收件人："));
        String response = "{\"documentTemplate\":\"source-version\",\"fieldValues\":[]}";
        var result = TemplateDrafting.read(TemplateDrafting.finish(response, input));
        assertEquals("接受{{委托人}}委托。", result.blocks().get(0).text());
    }

    @Test void conflictingSourceAndFilledValueAreRejectedInsteadOfOverwritten() throws Exception {
        String source = "委托人：合成甲银行。本次资料另记载合成乙银行。";
        String input = withTemplate(request(source, ""));
        String response = JSON.writeValueAsString(Map.of("documentTemplate", "source-version", "fieldValues", List.of(
            Map.of("fieldId", "p0-client", "value", "合成乙银行", "quote", source))));
        var error = assertThrows(DraftingException.class, () -> TemplateDrafting.finish(response, input));
        assertEquals("模板字段的来源值与填写值冲突，请核对本次资料后重试", error.getMessage());
    }

    @Test void numericallyEquivalentPrefillDoesNotCauseAConflict() {
        assertEquals("本金5000.00元。", TemplateSourceValues.fill("本金{{本金}}元。", "本金5,000元。", "", Map.of("principal", "5000.00")));
        assertEquals("发函日期2026年10月2日。", TemplateSourceValues.fill("发函日期{{发函日期}}。", "发函日期2026年10月02日。", "", Map.of("letterDate", "2026年10月2日")));
    }
    @Test void aFixedBankSuffixDoesNotTurnTheSameSourceIntoAConflict(){
        assertEquals("接受合成商业银行委托。",TemplateSourceValues.fill("接受{{委托人}}行委托。","接受合成商业银行行委托。","",Map.of("client","合成商业银行")));
        assertThrows(DraftingException.class,()->TemplateSourceValues.fill("接受{{委托人}}行委托。","接受另一银行行委托。","",Map.of("client","合成商业银行")));
    }

    private static String withTemplate(String input) throws Exception {
        var field = new TemplateFields.Field("p0-client", "p0", 2, 9, "{{委托人}}", "委托人", "text", "", "text", true);
        var block = new TemplateLayout.Block("p0", "接受{{委托人}}委托。", true, List.of(field));
        return input + "\n---版式填写JSON开始---\n" + JSON.writeValueAsString(new TemplateDrafting.Document("source-version", List.of(block)))
            + "\n---版式填写JSON结束---";
    }
}
