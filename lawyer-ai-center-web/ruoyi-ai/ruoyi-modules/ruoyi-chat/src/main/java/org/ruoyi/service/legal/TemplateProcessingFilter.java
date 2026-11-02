package org.ruoyi.service.legal;

import java.util.Set;

/** Metadata-only projection. Bind the authenticated tenant in every table: their legacy collations may differ. */
final class TemplateProcessingFilter {
    private TemplateProcessingFilter() {}
    static final Set<String> VALUES = Set.of("unparsed", "running", "review", "confirmed", "error", "text");
    static String expression(String value) {
        if (!VALUES.contains(value)) throw new DraftingException("请选择有效的模板处理状态", 400);
        return "(CASE (SELECT CASE WHEN t.state='running' AND t.expires_at<CURRENT_TIMESTAMP(6) THEN 'failed' ELSE t.state END "
            + "FROM legal_doc_template_field_task t WHERE t.tenant_id={1} AND t.template_id=legal_doc_template.id) "
            + "WHEN 'running' THEN 'running' WHEN 'failed' THEN 'error' WHEN 'stale' THEN 'error' "
            + "WHEN 'ready' THEN 'review' WHEN 'partial' THEN 'review' ELSE COALESCE(("
            + "SELECT CASE WHEN JSON_UNQUOTE(JSON_EXTRACT(f.snapshot_json,'$.parseStatus'))='uploaded' THEN 'unparsed' "
            + "WHEN JSON_UNQUOTE(JSON_EXTRACT(f.snapshot_json,'$.fieldsConfirmed'))='true' THEN 'confirmed' ELSE 'review' END "
            + "FROM legal_doc_template_file f WHERE f.tenant_id={1} AND f.template_id=legal_doc_template.id "
            + "ORDER BY f.bound_at DESC,f.version_id DESC LIMIT 1),'text') END) = {0}";
    }
}
