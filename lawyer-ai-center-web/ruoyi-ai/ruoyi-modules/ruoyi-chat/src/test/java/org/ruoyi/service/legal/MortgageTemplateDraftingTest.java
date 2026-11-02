package org.ruoyi.service.legal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic mortgage letter: independent field IDs, sources and immutable wording. */
class MortgageTemplateDraftingTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String VERSION = "8ddaa377-5052-49b5-87e9-9e2777a4e9d8";
    private static final String FACTS = """
        合成回归资料，仅供测试。个人一手住房贷款合同。
        委托人：测试银行支行。收件人：陈景安。
        承办律师：赵衡。律师联系电话：000-00000999。
        合同编号：TEST-MORTGAGE-01。
        首期逾期起算日：2026年10月2日。核算截止日：2026年10月22日。
        已到期逾期本金合计5000.00元。未付正常利息合计1650.00元。本息合计6650.00元。
        催告期限：签收次日起3个自然日。
        """;

    private record SourceField(String label, String kind, String unit, String value, String quote) {}
    private record Fixture(List<TemplateLayout.Block> originals, List<SourceField> sources) {
        List<TemplateLayout.Block> logical() { return TemplateFields.logical(originals); }
    }

    @Test
    void fourteenSourceBoundFieldsFillTheMortgageLetterWithoutChangingFixedWording() throws Exception {
        Fixture fixture = fixture();
        ObjectNode answer = answer(fixture);
        assertEquals(14, answer.path("fieldValues").size());

        var document = TemplateDrafting.read(TemplateDrafting.finish(answer.toString(), request(fixture, FACTS)));

        assertNotNull(document);
        assertEquals(VERSION, document.documentTemplate());
        assertEquals(List.of(
            "一手房按揭催款律师函",
            "收件人：陈景安先生/女士：",
            "接受测试银行支行的委托，指派赵衡律师。",
            "合同编号：TEST-MORTGAGE-01。贷款自2026年10月2日开始逾期，截止2026年10月22日，逾期本金5000.00元，正常利息1650.00元，本息合计6650.00元。",
            "电话：000-00000999",
            "收到本函之日起三日内，将到期款项付至合同约定账户。",
            "测试律师事务所"
        ), document.blocks().stream().map(TemplateLayout.Block::text).toList());
        assertEquals("{{收件人}}", fixture.logical().get(1).fields().get(0).original());
        assertTrue(fixture.originals().get(1).text().contains("____"));
        assertNotNull(document.calculationResults());
        assertTrue(document.calculationResults().stream()
            .filter(result -> "本息合计".equals(result.label()))
            .anyMatch(result -> "calculated".equals(result.status()) && "6650.00".equals(result.value())));
    }

    @Test
    void omittedFieldsWithoutSourcesRetainTheirPlaceholdersAndOtherFieldsStillFill() throws Exception {
        Fixture fixture = fixture();
        ObjectNode answer = answer(fixture);
        ArrayNode fields = JSON.createArrayNode();
        for (var row : answer.withArray("fieldValues")) {
            String label = label(fixture, row.path("fieldId").asText());
            if (!List.of("承办律师", "律师联系电话").contains(label)) fields.add(row);
        }
        answer.set("fieldValues", fields);
        String source = FACTS.replace("承办律师：赵衡。律师联系电话：000-00000999。", "承办信息另行提供。");

        var document = TemplateDrafting.read(TemplateDrafting.finish(answer.toString(), request(fixture, source)));

        assertNotNull(document);
        assertEquals("接受测试银行支行的委托，指派{{承办律师}}律师。", document.blocks().get(2).text());
        assertEquals("电话：{{律师联系电话}}", document.blocks().get(4).text());
        assertTrue(document.blocks().get(3).text().contains("逾期本金5000.00元"));
        assertFalse(TemplateDrafting.plain(document).contains("赵衡"));
        assertFalse(TemplateDrafting.plain(document).contains("待补充"));
    }

    @Test
    void aQuotedValueOutsideTheCurrentSourceIsRejected() throws Exception {
        Fixture fixture = fixture();
        ObjectNode answer = answer(fixture);
        ObjectNode contract = row(fixture, answer, "合同编号");
        contract.put("value", "TEST-FORGED-99");
        contract.put("quote", "合同编号：TEST-FORGED-99。");

        DraftingException error = assertThrows(DraftingException.class,
            () -> TemplateDrafting.finish(answer.toString(), request(fixture, FACTS)));

        assertEquals(422, error.getCode());
        assertTrue(error.getMessage().contains("原文依据"));
    }

    @Test
    void anUnknownFieldIdCannotInsertAnAdditionalField() throws Exception {
        Fixture fixture = fixture();
        ObjectNode answer = answer(fixture);
        ((ObjectNode) answer.withArray("fieldValues").get(0)).put("fieldId", "p999-invented-field");

        DraftingException error = assertThrows(DraftingException.class,
            () -> TemplateDrafting.finish(answer.toString(), request(fixture, FACTS)));

        assertEquals(422, error.getCode());
        assertTrue(error.getMessage().contains("未知"));
    }

    @Test
    void duplicateFieldIdsAndRepeatedFixedUnitsAreRejected() throws Exception {
        Fixture fixture = fixture();
        ObjectNode duplicate = answer(fixture);
        duplicate.withArray("fieldValues").add(duplicate.withArray("fieldValues").get(0).deepCopy());
        assertThrows(DraftingException.class,
            () -> TemplateDrafting.finish(duplicate.toString(), request(fixture, FACTS)));

        ObjectNode repeatedUnit = answer(fixture);
        row(fixture, repeatedUnit, "逾期本金").put("value", "5000.00元");
        DraftingException error = assertThrows(DraftingException.class,
            () -> TemplateDrafting.finish(repeatedUnit.toString(), request(fixture, FACTS)));
        assertTrue(error.getMessage().contains("固定单位"));
    }

    private static Fixture fixture() {
        List<TemplateLayout.Block> blocks = new ArrayList<>();
        List<SourceField> sources = new ArrayList<>();
        blocks.add(new TemplateLayout.Block("p0", "一手房按揭催款律师函", false, List.of()));
        blocks.add(block("p1", "收件人：____先生/女士：", sources,
            field("收件人", "text", "", "陈景安", "收件人：陈景安。")));
        blocks.add(block("p2", "接受____的委托，指派____律师。", sources,
            field("委托人", "text", "", "测试银行支行", "委托人：测试银行支行。"),
            field("承办律师", "text", "", "赵衡", "承办律师：赵衡。")));
        blocks.add(block("p3", "合同编号：____。贷款自____年____月____日开始逾期，截止____年____月____日，逾期本金____元，正常利息____元，本息合计____元。", sources,
            field("合同编号", "text", "", "TEST-MORTGAGE-01", "合同编号：TEST-MORTGAGE-01。"),
            field("逾期起算日（年）", "number", "年", "2026", "首期逾期起算日：2026年10月2日。"),
            field("逾期起算日（月）", "number", "月", "10", "首期逾期起算日：2026年10月2日。"),
            field("逾期起算日（日）", "number", "日", "2", "首期逾期起算日：2026年10月2日。"),
            field("核算截止日（年）", "number", "年", "2026", "核算截止日：2026年10月22日。"),
            field("核算截止日（月）", "number", "月", "10", "核算截止日：2026年10月22日。"),
            field("核算截止日（日）", "number", "日", "22", "核算截止日：2026年10月22日。"),
            field("逾期本金", "money", "元", "5000.00", "已到期逾期本金合计5000.00元。"),
            field("正常利息", "money", "元", "1650.00", "未付正常利息合计1650.00元。"),
            field("本息合计", "money", "元", "6650.00", "本息合计6650.00元。")));
        blocks.add(block("p4", "电话：____", sources,
            field("律师联系电话", "text", "", "000-00000999", "律师联系电话：000-00000999。")));
        blocks.add(new TemplateLayout.Block("p5", "收到本函之日起三日内，将到期款项付至合同约定账户。", false, List.of()));
        blocks.add(new TemplateLayout.Block("p6", "测试律师事务所", false, List.of()));
        return new Fixture(List.copyOf(blocks), List.copyOf(sources));
    }

    private static SourceField field(String label, String kind, String unit, String value, String quote) {
        return new SourceField(label, kind, unit, value, quote);
    }

    private static TemplateLayout.Block block(String id, String text, List<SourceField> sources, SourceField... declarations) {
        List<TemplateFields.Field> fields = new ArrayList<>();
        int position = 0;
        for (SourceField source : declarations) {
            int start = text.indexOf("____", position);
            if (start < 0) throw new IllegalArgumentException("Missing synthetic field span");
            int end = start + 4;
            fields.add(new TemplateFields.Field(id + "-" + start + "-" + end, id, start, end, "____",
                source.label(), source.kind(), source.unit(), "underline", true));
            sources.add(source);
            position = end;
        }
        return new TemplateLayout.Block(id, text, true, fields);
    }

    private static ObjectNode answer(Fixture fixture) {
        ObjectNode answer = JSON.createObjectNode();
        answer.put("documentTemplate", VERSION);
        ArrayNode values = answer.putArray("fieldValues");
        int index = 0;
        for (var block : fixture.originals()) for (var field : block.fields()) {
            SourceField source = fixture.sources().get(index++);
            values.addObject().put("fieldId", field.id()).put("value", source.value()).put("quote", source.quote());
        }
        answer.putArray("calculations");
        return answer;
    }

    private static String request(Fixture fixture, String facts) throws Exception {
        return "请仅按本次资料填写律师函。\n---起草要素---\n"
            + JSON.writeValueAsString(new DraftingFacts.Factors("测试银行支行、陈景安", facts, "催收已到期本金和正常利息", "催告期限3个自然日。"))
            + "\n---版式填写JSON开始---\n"
            + JSON.writeValueAsString(new TemplateDrafting.Document(VERSION, fixture.logical()))
            + "\n---版式填写JSON结束---";
    }

    private static String label(Fixture fixture, String id) {
        return fixture.originals().stream().flatMap(block -> block.fields().stream())
            .filter(field -> field.id().equals(id)).findFirst().orElseThrow().label();
    }

    private static ObjectNode row(Fixture fixture, ObjectNode answer, String label) {
        for (var row : answer.withArray("fieldValues"))
            if (label.equals(label(fixture, row.path("fieldId").asText()))) return (ObjectNode) row;
        throw new IllegalArgumentException("Missing synthetic field");
    }
}
