package org.ruoyi.service.legal;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;

/** Versioned query snapshot. The DOCX copy remains available for layout-preserving generation. */
final class TemplateSnapshot {
    private static final ObjectMapper JSON = new ObjectMapper();
    record Data(int schemaVersion, List<TemplateLayout.Block> blocks, TemplateLayout.State state,
                String parseStatus, String warning, boolean fieldsConfirmed) {}
    private TemplateSnapshot() {}

    static Data create(List<TemplateLayout.Block> blocks, TemplateLayout.State state, Long templateId) {
        if (blocks == null) throw damaged();
        String status = state == null ? (templateId == null ? "rules" : "confirmed")
            : state.raw() ? "uploaded" : state.confirmed() ? "confirmed" : "AI".equals(state.method()) ? "parsed" : "rules";
        return new Data(1, List.copyOf(blocks), state, status, state == null ? "" : state.warning(), "confirmed".equals(status));
    }
    static String encode(List<TemplateLayout.Block> blocks, TemplateLayout.State state, Long templateId) {
        try { return JSON.writeValueAsString(create(blocks, state, templateId)); }
        catch (Exception e) { throw damaged(); }
    }
    static Data decode(String value, Long templateId) {
        try {
            Data data = JSON.readValue(value, Data.class);
            if (data == null || data.schemaVersion() != 1 || data.blocks() == null) throw damaged();
            Data expected = create(data.blocks(), data.state(), templateId);
            if (!Objects.equals(expected.parseStatus(), data.parseStatus()) || !Objects.equals(expected.warning(), data.warning()) || expected.fieldsConfirmed() != data.fieldsConfirmed()) throw damaged();
            return data;
        } catch (DraftingException e) { throw e; }
        catch (Exception e) { throw damaged(); }
    }
    private static DraftingException damaged() { return new DraftingException("模板配置快照损坏，请联系管理员修复版本数据", 422); }
}
