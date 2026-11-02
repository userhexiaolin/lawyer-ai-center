package org.ruoyi.service.legal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.ruoyi.common.core.exception.ServiceException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import java.io.ByteArrayInputStream;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class LegalOpinionDocumentTest {
    public static String sample() throws Exception {
        var sections=new ArrayList<Map<String,Object>>();
        for(String key:LegalOpinionDocument.KEYS)sections.add(new LinkedHashMap<>(Map.of("key",key,"content","依据 → 事实匹配 → 结论；2026年1月8日付款3000元。","citations",List.of())));
        sections.get(2).put("citations",List.of(Map.of("type","law","lawName","测试法规","articleNo","12","status","repealed","sourceUrl","https://example.org/law","fragmentId","123")));
        Collections.reverse(sections);
        return new ObjectMapper().writeValueAsString(Map.of("title","测试意见书","sections",sections));
    }
    @Test void canonicalOrderCitationsAndReadableWord() throws Exception {
        var document=LegalOpinionDocument.parse(sample());
        assertEquals(LegalOpinionDocument.KEYS,document.sections().stream().map(LegalOpinionDocument.Section::key).toList());
        assertEquals(1,document.sections().get(5).citations().size());
        String md=LegalOpinionDocument.markdown(document);
        assertTrue(md.contains("《测试法规》第12条（已失效）"));
        try(var doc=new XWPFDocument(new ByteArrayInputStream(LegalOpinionDocument.word(document)));var extractor=new XWPFWordExtractor(doc)) {
            String text=extractor.getText();for(String title:LegalOpinionDocument.TITLES)assertTrue(text.contains(title));
            assertTrue(text.contains("3000元"));assertTrue(text.contains("已失效"));assertTrue(text.contains("https://example.org/law"));
            assertTrue(doc.getParagraphs().stream().anyMatch(p->!p.getIRuns().isEmpty()&&p.getCTP().sizeOfHyperlinkArray()>0));
        }
    }
    @Test void incompleteDuplicateAndUnsafeReferencesRejected() throws Exception {
        for(String source:List.of("null","{}","文字","{\"sections\":[]}",sample().replace("\"strategy\"","\"timeline\""),sample().replace("https://example.org/law","javascript:alert(1)"),sample()+"{}"))assertThrows(ServiceException.class,()->LegalOpinionDocument.parse(source),source);
    }
    @Test void emptyKnowledgeNeverCreatesCaseNumberAndAddsSearchNotice() throws Exception {
        var root=new ObjectMapper().readTree(sample());for(var section:root.path("sections"))((com.fasterxml.jackson.databind.node.ObjectNode)section).putArray("citations");
        var document=LegalOpinionDocument.parse(root.toString());assertTrue(document.sections().get(5).citations().isEmpty());assertTrue(document.sections().get(5).content().contains("建议进一步检索"));
    }
    @Test void dedicatedAgentAlwaysGetsProtocolOtherAgentsUnchanged() {
        assertEquals("普通助手",LegalOpinionDocument.agentPrompt("普通助手","普通助手"));
        String prompt=LegalOpinionDocument.agentPrompt(LegalOpinionDocument.AGENT_NAME,"管理员前言");
        assertTrue(prompt.startsWith("管理员前言"));for(String key:LegalOpinionDocument.KEYS)assertTrue(prompt.contains(key));
        assertEquals(prompt,LegalOpinionDocument.agentPrompt(LegalOpinionDocument.AGENT_NAME,prompt));assertTrue(prompt.contains("禁止编造"));
    }
}
