package org.ruoyi.service.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.ruoyi.common.core.exception.ServiceException;
import java.io.ByteArrayInputStream;
import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class ContractReviewReportTest {
    @Test void issuesExportUsesQuoteSuggestionLocationAndStructuredRefs() throws Exception {
        String source="""
            {"contractType":"测试合同","summary":"摘要","risks":[{"title":"不应导出旧风险"}],"issues":[
            {"id":"R1","riskLevel":"high","clauseTitle":"新风险","quote":"原文不得丢失","location":{"page":2,"paragraph":3,"positionText":"第二条"},"problem":"需核查","suggestion":"修改交付条件",
             "lawRefs":[{"lawName":"测试法规","articleNo":"12","url":"https://example.org/law"}],"caseRefs":[{"caseNo":"合成测试编号","url":"https://example.org/case"}]},
            {"id":"R2","riskLevel":"low","clauseTitle":"空引用","location":{},"lawRefs":[],"caseRefs":[]}]}
            """;
        var result=ContractReviewReport.parse(source);
        assertEquals(2,result.path("issues").size());
        try(var doc=new XWPFDocument(new ByteArrayInputStream(ContractReviewReport.generate("```json\n"+source+"\n```"))); var extractor=new XWPFWordExtractor(doc)) {
            String text=extractor.getText();
            for(String expected:new String[]{"测试合同","原文摘录","原文不得丢失","修改交付条件","第2页","第3段","《测试法规》第12条","https://example.org/law","合成测试编号"}) assertTrue(text.contains(expected),expected);
            assertFalse(text.contains("不应导出旧风险"));
            var emptyRow=doc.getTables().get(0).getRow(2);
            assertEquals("",emptyRow.getCell(4).getText()); assertEquals("",emptyRow.getCell(5).getText());
            assertEquals("",emptyRow.getCell(6).getText()); assertEquals("",emptyRow.getCell(7).getText());
        }
        assertThrows(ServiceException.class,()->ContractReviewReport.generate("{\"summary\":\"拒绝降级\",\"issues\":null,\"risks\":[]}"));
    }
    @Test void structuredCitationsRemainVisibleInExport() throws Exception {
        var result = ContractReviewReport.parse("""
            {"summary":"测试","risks":[{"title":"风险","legalBasis":[
              {"type":"law","lawName":"测试法规","articleNo":"12","status":"repealed","sourceUrl":"https://example.org/law"},
              {"type":"case","caseNo":"（测试）1号"}]}]}
            """);
        String citations = ContractReviewReport.list(result.path("risks").get(0).path("legalBasis"));
        assertTrue(citations.contains("《测试法规》第12条（已失效）"));
        assertTrue(citations.contains("https://example.org/law")); assertTrue(citations.contains("（测试）1号"));
    }
    @Test void acceptsCompleteTrailingCommaWithoutChangingQuotedSource() throws Exception {
        var value=ContractReviewReport.parse("{\"summary\":\"引用,}原样\",\"risks\":[{\"title\":\"风险\",},],}");
        assertEquals("引用,}原样",value.path("summary").asText());
        assertEquals(1,value.path("risks").size());
        assertThrows(ServiceException.class,()->ContractReviewReport.parse("{\"summary\":\"截断\",\"risks\":[{\"title\":\"风险\",}"));
    }
    @Test void exportsChineseFieldsAndSortedRisksWithoutLosingSourceText() throws Exception {
        String source = """
            {"summary":"存在付款风险","riskLevel":"high","basicInfo":{"title":"服务合同","parties":["张三","李四"]},
            "risks":[{"title":"低风险点","level":"low"},{"title":"高风险点","level":"high","clause":"金额3000元 <原文>","position":"第1条","analysis":"约定不清","suggestion":"明确期限","legalBasis":["法律依据待核实"]}],
            "missingClauses":["解除条款"],"overallSuggestion":"签约前修改"}
            """;
        byte[] bytes = ContractReviewReport.generate("```json\n" + source + "\n```");
        try (var doc = new XWPFDocument(new ByteArrayInputStream(bytes)); var extractor = new XWPFWordExtractor(doc)) {
            String text = extractor.getText();
            for (String value : new String[]{"合同审查报告","服务合同","张三","李四","金额3000元 <原文>","第1条","明确期限","解除条款","签约前修改","仅供参考"}) assertTrue(text.contains(value), value);
            assertTrue(text.indexOf("高风险点") < text.indexOf("低风险点"));
        }
    }
    @Test void rejectsTruncatedOrMalformedResults() {
        for (String source : new String[]{"", "普通文字", "{\"summary\":\"截断\"", "{\"summary\":\"缺少风险列表\"}", "{\"summary\":\"非法风险\",\"risks\":[{}]}"}) {
            assertThrows(ServiceException.class, () -> ContractReviewReport.generate(source));
        }
    }
}
