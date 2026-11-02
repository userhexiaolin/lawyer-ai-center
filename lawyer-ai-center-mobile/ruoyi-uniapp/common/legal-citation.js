// Formal mobile source. Protocol compatibility is verified by tests, not patch overwrites.
const text = (v) => typeof v === 'string' ? v.trim() : '';
export function normalizeCitation(value) {
    var _a, _b, _c, _d, _e;
    if (!value || typeof value !== 'object')
        return null;
    const v = value;
    const common = { sourceUrl: text(v.sourceUrl) || text(v.source_url) || text(v.url), fragmentId: text(v.fragmentId) || text(v.fragment_id) };
    const caseNo = text((_a = v.caseNo) !== null && _a !== void 0 ? _a : v.case_no);
    if (v.type === 'case' || caseNo)
        return caseNo ? Object.assign({ type: 'case', caseNo, title: text(v.title), court: text(v.court) }, common) : null;
    const lawName = text((_b = v.lawName) !== null && _b !== void 0 ? _b : v.law_name), articleNo = text((_c = v.articleNo) !== null && _c !== void 0 ? _c : v.article_no);
    if (!lawName || !articleNo)
        return null;
    const raw = text((_e = (_d = v.status) !== null && _d !== void 0 ? _d : v.validityStatus) !== null && _e !== void 0 ? _e : v.validity_status);
    const status = { '有效': 'effective', '已失效': 'repealed', '已修订': 'amended', '已修改': 'amended' }[raw] || raw;
    return Object.assign({ type: 'law', lawName, articleNo, status }, common);
}
export function citationLabel(c) {
    if (c.type === 'case')
        return [c.caseNo, c.title, c.court].filter(Boolean).join(' · ');
    const article = /^第/.test(c.articleNo) ? c.articleNo : `第${c.articleNo.replace(/条$/, '')}条`;
    return `《${c.lawName.replace(/[《》]/g, '')}》${article}`;
}
export function citationWarning(c) {
    if (c.type !== 'law' || c.status === 'effective')
        return '';
    return c.status === 'repealed' ? '已失效' : c.status === 'amended' ? '已修改' : '时效待核实';
}
export function citationTarget(c) {
    if (c.fragmentId)
        return { kind: 'fragment', value: c.fragmentId };
    if (c.sourceUrl && /^https?:\/\//i.test(c.sourceUrl))
        return { kind: 'source', value: c.sourceUrl };
    return { kind: 'missing', value: '' };
}
/** Text responses embed a fenced `legal-citations` JSON array. Incomplete streams stay as text. */
export function splitLegalContent(content) {
    const citations = [];
    const markdown = content.replace(/```legal-citations\s*\r?\n([\s\S]*?)```/g, (block, json) => {
        try {
            const values = JSON.parse(json);
            if (!Array.isArray(values))
                return block;
            const normalized = values.map(normalizeCitation);
            if (normalized.some(v => !v))
                return block;
            citations.push(...normalized);
            return '';
        }
        catch (_a) {
            return block;
        }
    });
    return { markdown, citations };
}
