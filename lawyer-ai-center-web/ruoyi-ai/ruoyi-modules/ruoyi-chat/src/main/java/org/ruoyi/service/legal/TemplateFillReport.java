package org.ruoyi.service.legal;

import java.util.*;

/** Coverage describes a draft's completeness, never a legal or human-review approval. */
public final class TemplateFillReport {
    private TemplateFillReport() {}
    public record Issue(String fieldId, String blockId, String label, String reason) {}
    public record Report(String status, int totalFields, int filledFields, List<Issue> issues) {}

    public static Report assess(List<TemplateLayout.Block> expected, List<TemplateLayout.Block> actual,
                                List<DraftingCalculations.Result> calculations) {
        Map<String, TemplateLayout.Block> filled = new HashMap<>();
        actual.forEach(block -> filled.put(block.id(), block));
        List<Issue> issues = new ArrayList<>();
        int total = 0, complete = 0;
        for (var block : expected) {
            if (!block.editable()) continue;
            var ranges = TemplateFields.ranges(block);
            var values = TemplateFields.values(block, filled.get(block.id()).text());
            List<TemplateFields.Field> fields = block.fields() == null ? List.of()
                : block.fields().stream().filter(TemplateFields.Field::enabled).toList();
            for (int i = 0; i < ranges.size(); i++) {
                var range = ranges.get(i);
                String original = block.text().substring(range[0], range[1]);
                String value = values.get(i);
                String label = fields.isEmpty() ? original.startsWith("{{")
                    ? original.substring(2, original.length() - 2) : "填写项" + (i + 1) : fields.get(i).label();
                String id = fields.isEmpty() ? block.id() + ":" + i : fields.get(i).id();
                total++;
                if (value.equals(original) || !TemplateSourceValues.meaningfulValue(value)) {
                    issues.add(new Issue(id, block.id(), label, "unfilled"));
                } else complete++;
            }
            // Old confirmed snapshots may have omitted a caption or semantic edge blank.
            // Report the gap without inventing permission to modify that old version.
            if (block.fields() != null) {
                for (var range : TemplateFields.ranges(block.text())) {
                    // Disabled reviewed fields are intentional; only absent candidates are gaps.
                    boolean configured = block.fields().stream().anyMatch(field -> field.start() <= range[0] && field.end() >= range[1]);
                    if (configured) continue;
                    var candidate = TemplateFields.candidate(block.id(), range[0], range[1], block.text(), "", false);
                    if (candidate.enabled() && !candidate.label().equals("填写项")) {
                        issues.add(new Issue(candidate.id(), block.id(), candidate.label(), "template_configuration"));
                    }
                }
            }
        }
        boolean configuration = issues.stream().anyMatch(issue -> issue.reason().equals("template_configuration"));
        boolean pending = calculations != null && calculations.stream().anyMatch(result -> !"calculated".equals(result.status()));
        String status = configuration ? "configuration_required" : !issues.isEmpty() || pending ? "partial" : "complete";
        return new Report(status, total, complete, List.copyOf(issues));
    }
}
