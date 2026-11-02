package org.ruoyi.service.legal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class GenericTemplateTextSourceTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void arbitraryTextLabelsUseTheirOwnExplicitSourceValues() throws Exception {
        String request = request("合成客户：测试主体", "客户名称：合成服务公司。客户编号：CUS-42。送达邮编：330000。", "",
            Map.of("客户名称", "text", "客户编号", "text", "送达邮编", "text"));
        var values = TemplateSourceValues.read(request);
        assertEquals("合成服务公司", values.get("field:客户名称"));
        assertEquals("CUS-42", values.get("field:客户编号"));
        assertEquals("330000", values.get("field:送达邮编"));
        var result = TemplateDrafting.read(TemplateDrafting.finish(emptyAnswer(), request));
        assertTrue(TemplateDrafting.plain(result).contains("客户名称：合成服务公司。"));
        assertTrue(TemplateDrafting.plain(result).contains("客户编号：CUS-42。"));
        assertTrue(TemplateDrafting.plain(result).contains("送达邮编：330000。"));
    }

    @Test void aDifferentLabelCannotBecomeTheValueOfTheRequestedField() throws Exception {
        String request = request("合成客户：测试主体", "客户名称：合成服务公司。", "", Map.of("客户名称", "text"));
        String block = TemplateDrafting.expected(request).blocks().get(0).id();
        String response = JSON.writeValueAsString(Map.of("documentTemplate", "generic-version", "fieldValues", List.of(
            Map.of("fieldId", block + "-value", "value", "合成客户", "quote", "合成客户：测试主体"))));
        var error = assertThrows(DraftingException.class, () -> TemplateDrafting.finish(response, request));
        assertEquals("模板字段的来源值与填写值冲突，请核对本次资料后重试", error.getMessage());
    }

    @Test void multipleLabelsOnOneLineAreSeparatedWithoutLosingCodesOrPostalZeros() throws Exception {
        for (String separator : List.of("，", " ", "；")) {
            String facts = "客户名称：合成服务公司" + separator + "客户编号：CUS-42" + separator + "送达邮编：003300。";
            var values = TemplateSourceValues.read(request("", facts, "", Map.of("客户名称", "text", "客户编号", "text", "送达邮编", "text")));
            assertEquals("合成服务公司", values.get("field:客户名称"));
            assertEquals("CUS-42", values.get("field:客户编号"));
            assertEquals("003300", values.get("field:送达邮编"));
        }
    }

    @Test void missingAmbiguousOrNonMatchingLabelsRemainUnresolved() throws Exception {
        for (String facts : List.of("客户全称：合成服务公司。", "客户名称：合成甲公司。客户名称：合成乙公司。",
            "客户名称：\n客户编号：CUS-42", "客户名称：待核实。", "客户名称：{{客户名称}}。", "客户名称：客户名称。", "客户名称：客户编号。", "非客户名称：合成甲公司。", "合同客户名称：合成甲公司。")) {
            var values = TemplateSourceValues.read(request("", facts, "", Map.of("客户名称", "text", "客户编号", "text")));
            assertFalse(values.containsKey("field:客户名称"), facts);
        }
    }

    @Test void concatenatedLabelsDoNotRelaxTheExactLabelBoundary() throws Exception {
        String request = request("", "客户名称：合成公司客户编号：C1。", "", Map.of("客户名称", "text", "客户编号", "text"));
        assertFalse(TemplateSourceValues.read(request).containsKey("field:客户编号"));
    }

    @Test void templateTextIsNeverUsedAsSourceEvidence() throws Exception {
        String request = request("", "本次资料只记载合同内容。", "", Map.of("客户名称", "text"));
        request = request.replace("客户名称：{{客户名称}}。", "模板示例客户名称：虚构公司。客户名称：{{客户名称}}。");
        assertFalse(TemplateSourceValues.read(request).containsKey("field:客户名称"));
    }

    @Test void moneyDateAndNumberFieldsNeverUseTheGenericTextPrefill() throws Exception {
        String request = request("", "应付金额：5000。送达日期：2026年10月28日。账期天数：30。客户名称：合成公司。", "",
            Map.of("应付金额", "money", "送达日期", "date", "账期天数", "number", "客户名称", "text"));
        var values = TemplateSourceValues.read(request);
        assertFalse(values.containsKey("field:应付金额"));
        assertFalse(values.containsKey("field:送达日期"));
        assertFalse(values.containsKey("field:账期天数"));
        assertEquals("合成公司", values.get("field:客户名称"));
    }

    @Test void dynamicBoundariesIncludeNonTextFieldsWithoutPrefillingTheirValues() throws Exception {
        String request = request("", "客户名称：合成公司，应付金额：5000元，客户编号：C1。", "",
            Map.of("客户名称", "text", "应付金额", "money", "客户编号", "text"));
        var values = TemplateSourceValues.read(request);
        assertEquals("合成公司", values.get("field:客户名称"));
        assertEquals("C1", values.get("field:客户编号"));
        assertFalse(values.containsKey("field:应付金额"));
    }

    @Test void establishedSemanticAmountsRemainAuthoritativeOverGenericTextAliases() throws Exception {
        String request = request("", "已到期逾期本金合计5000元。本金：9999元。", "", Map.of("本金", "text"));
        var known = TemplateSourceValues.read(request);
        assertEquals("5000.00", known.get("principal"));
        var result = TemplateDrafting.read(TemplateDrafting.finish(emptyAnswer(), request));
        assertTrue(TemplateDrafting.plain(result).contains("本金：5000.00。"));
    }

    private static String emptyAnswer() {
        return "{\"documentTemplate\":\"generic-version\",\"fieldValues\":[]}";
    }

    private static String request(String parties, String facts, String purpose, Map<String,String> fields) throws Exception {
        List<TemplateLayout.Block> blocks = new ArrayList<>();
        for (var entry : fields.entrySet()) {
            String id = "p" + blocks.size(), label = entry.getKey(), marker = "{{" + label + "}}";
            String text = label + "：" + marker + "。";
            int start = label.length() + 1;
            var field = new TemplateFields.Field(id + "-value", id, start, start + marker.length(), marker, label, entry.getValue(), "", "text", true);
            blocks.add(new TemplateLayout.Block(id, text, true, List.of(field)));
        }
        return "---起草要素---\n" + JSON.writeValueAsString(new DraftingFacts.Factors(parties, facts, purpose, ""))
            + "\n---版式填写JSON开始---\n" + JSON.writeValueAsString(new TemplateDrafting.Document("generic-version", blocks)) + "\n---版式填写JSON结束---";
    }
}
