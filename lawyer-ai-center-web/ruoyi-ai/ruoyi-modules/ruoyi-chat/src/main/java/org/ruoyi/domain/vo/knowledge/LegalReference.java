package org.ruoyi.domain.vo.knowledge;

import java.io.Serializable;

/** Alpha §16 wire contract. Identifiers and source locations must come from stored metadata. */
public record LegalReference(String type, String lawName, String articleNo, String status,
    String caseNo, String title, String court, String sourceUrl, String fragmentId) implements Serializable {
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();
    public String toJson() {
        try { return JSON.writeValueAsString(this); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("引用元数据序列化失败", e); }
    }
    public static final String PROMPT = """
        法律引用约定：引用法规必须有 lawName、articleNo；类案必须有真实 caseNo，可带 title、court。
        仅使用检索材料或用户提供的元数据，逐字保留编号、status、sourceUrl、fragmentId，不推测或编造案号与来源。
        引用对象格式：{type:"law",lawName,articleNo,status?,sourceUrl?,fragmentId?} 或 {type:"case",caseNo,title?,court?,sourceUrl?,fragmentId?}。
        普通回答/意见书在引用处后另起一个 legal-citations 代码块，内容为上述对象的 JSON 数组。
        专门约定 JSON 章节数组的意见书，将同样的引用对象放入章节 citations 数组，不另起代码块。
        合同审查必须使用 issues 新 JSON 结构，在 issues[].lawRefs 和 issues[].caseRefs 数组中直接放引用对象；未知时明确空数组，禁止输出旧 risks 模板或在 JSON 外添加代码块。
        status 非 effective 时在正文明确提示；repealed 为已失效，amended 为已修改。无状态时提示时效待核实。
        未提供检索材料或无检索命中时明确写“知识库暂无匹配”，不输出类案引用对象，不编造案号；可提供需核实的通用分析。
        """;
}
