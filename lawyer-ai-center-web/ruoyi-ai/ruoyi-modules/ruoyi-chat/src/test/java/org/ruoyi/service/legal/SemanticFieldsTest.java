package org.ruoyi.service.legal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SemanticFieldsTest {
    @Test
    void captionAnchorsAndRealBoundaryBlanksSurviveValidationAndOriginalDocxFill() throws Exception {
        byte[] source = document("致：", "  律师", "电话：   ", "姓名:", "地址：", "  固定条款不用于填写。  ", "本条说明如下：");
        byte[] unchanged = source.clone();
        var inspected = TemplateLayout.inspect(source, true);
        assertEquals(List.of("致：", "  律师", "电话：   ", "姓名:", "地址：", "  固定条款不用于填写。  ", "本条说明如下："),
            inspected.stream().map(TemplateLayout.Block::text).toList());
        var fields = inspected.stream().flatMap(block -> block.fields().stream()).toList();
        assertEquals(List.of("收件人", "承办律师", "电话", "姓名", "地址"),
            fields.stream().map(TemplateFields.Field::label).toList());
        assertEquals(fields, TemplateFields.validate(inspected, fields));
        assertEquals(2, fields.get(0).start());
        assertEquals(2, fields.get(0).end());
        assertEquals("", fields.get(0).original());
        assertEquals("  ", fields.get(1).original());
        assertEquals("   ", fields.get(2).original());
        var originals = inspected.stream().map(block -> new TemplateLayout.Block(block.id(), block.text(),
            block.fields().stream().anyMatch(TemplateFields.Field::enabled), block.fields())).toList();
        var logical = TemplateFields.logical(originals);
        assertEquals("致：{{收件人}}", logical.get(0).text());
        assertEquals("{{承办律师}}律师", logical.get(1).text());
        assertEquals("电话：{{电话}}", logical.get(2).text());
        Map<String, String> values = Map.of("收件人", "测试收件人", "承办律师", "测试律师", "电话", "000-00000999", "姓名", "测试姓名", "地址", "测试地址");
        List<TemplateLayout.Block> filled = new ArrayList<>();
        for (var block : logical) {
            String text = block.text();
            for (var field : block.fields()) if (field.enabled()) text = text.replace("{{" + field.label() + "}}", values.get(field.label()));
            filled.add(new TemplateLayout.Block(block.id(), text, block.editable()));
        }
        byte[] output = TemplateLayout.fill(source, filled, originals);
        assertEquals(List.of("致：测试收件人", "测试律师律师", "电话：000-00000999", "姓名:测试姓名", "地址：测试地址", "  固定条款不用于填写。  ", "本条说明如下："),
            TemplateLayout.inspect(output).stream().map(TemplateLayout.Block::text).toList());
        assertArrayEquals(unchanged, source);
        assertEquals(inspected, TemplateLayout.inspect(source, true));
    }

    @Test
    void onlyServerIdentifiedStandaloneCaptionAnchorsAreAccepted() throws Exception {
        var blocks = TemplateLayout.inspect(document("收件人：", "固定说明：", "本条通知收件人：", "姓名："), true);
        var recipient = blocks.get(0).fields().get(0);
        assertDoesNotThrow(() -> TemplateFields.validate(blocks, List.of(recipient)));
        assertTrue(blocks.get(1).fields().isEmpty());
        assertTrue(blocks.get(2).fields().isEmpty());
        assertThrows(DraftingException.class, () -> TemplateFields.validate(blocks, List.of(
            copy(recipient, "client-invented-id", recipient.start(), recipient.end(), true))));
        assertThrows(DraftingException.class, () -> TemplateFields.validate(blocks, List.of(
            copy(recipient, recipient.id(), 0, 0, true))));
        var unknown = new TemplateFields.Field("unknown", "p1", blocks.get(1).text().length(), blocks.get(1).text().length(), "", "说明", "text", "", "text", true);
        assertThrows(DraftingException.class, () -> TemplateFields.validate(blocks, List.of(unknown)));
        var notInspected = List.of(new TemplateLayout.Block("p0", "收件人：", false, List.of()));
        assertThrows(DraftingException.class, () -> TemplateFields.validate(notInspected, List.of(recipient)));
        assertEquals("", TemplateFields.captionLabel("本条通知收件人："));
        assertEquals("", TemplateFields.captionLabel("说明："));
        assertEquals("收件人", TemplateFields.captionLabel("　致："));
    }

    @Test
    void disabledMetadataSurvivesLogicalPromptAndDocxRoundTripWithoutBeingEnabled() throws Exception {
        byte[] source = document("收件人：", "合同编号：____；电话：____；姓名：____");
        var inspected = TemplateLayout.inspect(source, true);
        var caption = inspected.get(0).fields().get(0);
        var fields = inspected.get(1).fields();
        assertEquals(3, fields.size());
        List<TemplateLayout.Block> originals = List.of(
            new TemplateLayout.Block("p0", inspected.get(0).text(), true, List.of(copy(caption, caption.id(), caption.start(), caption.end(), false))),
            new TemplateLayout.Block("p1", inspected.get(1).text(), true, List.of(fields.get(0),
                copy(fields.get(1), fields.get(1).id(), fields.get(1).start(), fields.get(1).end(), false), fields.get(2))));
        var all = originals.stream().flatMap(block -> block.fields().stream()).toList();
        assertEquals(all, TemplateFields.validate(inspected, all));
        var logical = TemplateFields.logical(originals);
        assertFalse(logical.get(0).fields().get(0).enabled());
        assertEquals("收件人：", logical.get(0).text());
        assertEquals("合同编号：{{合同编号}}；电话：____；姓名：{{姓名}}", logical.get(1).text());
        var disabled = logical.get(1).fields().get(1);
        assertFalse(disabled.enabled());
        assertEquals("____", logical.get(1).text().substring(disabled.start(), disabled.end()));
        assertNotEquals(fields.get(1).start(), disabled.start());
        String version = "82a00fe5-75ef-4e39-98af-95dc67011235";
        var v = new TemplateFiles.Version(version, 1L, "合成模板.docx", "docx", source, source, originals);
        assertEquals(logical, TemplateDrafting.expected(TemplateDrafting.prompt(v)).blocks());
        var json = new ObjectMapper();
        var decoded = json.readValue(json.writeValueAsString(originals), new com.fasterxml.jackson.core.type.TypeReference<List<TemplateLayout.Block>>() {});
        assertEquals(logical, TemplateFields.logical(decoded));
        assertEquals(originals, TemplateLayout.storedFields(TemplateLayout.storeFields(source, originals), List.of()));
        byte[] filled = TemplateLayout.fill(source, List.of(
            new TemplateLayout.Block("p0", "收件人：", true),
            new TemplateLayout.Block("p1", "合同编号：TEST-01；电话：____；姓名：测试姓名", true)), originals);
        assertEquals("收件人：", TemplateLayout.inspect(filled).get(0).text());
        assertEquals("合同编号：TEST-01；电话：____；姓名：测试姓名", TemplateLayout.inspect(filled).get(1).text());
        assertFalse(TemplateFields.logical(originals).get(0).fields().get(0).enabled());
    }

    private static TemplateFields.Field copy(TemplateFields.Field field, String id, int start, int end, boolean enabled) {
        return new TemplateFields.Field(id, field.blockId(), start, end, field.original(), field.label(), field.kind(), field.unit(), field.presentation(), enabled);
    }

    private static byte[] document(String... paragraphs) throws Exception {
        try (var doc = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
            for (String text : paragraphs) doc.createParagraph().createRun().setText(text);
            doc.write(out);
            return out.toByteArray();
        }
    }
}
