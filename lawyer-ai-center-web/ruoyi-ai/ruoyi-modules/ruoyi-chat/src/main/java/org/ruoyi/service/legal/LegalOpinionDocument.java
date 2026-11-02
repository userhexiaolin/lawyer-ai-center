package org.ruoyi.service.legal;

import com.fasterxml.jackson.databind.*;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.domain.vo.knowledge.LegalReference;
import org.apache.poi.xwpf.usermodel.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** One canonical six-section contract for agent prompting, history, Markdown and POI export. */
public final class LegalOpinionDocument {
    private LegalOpinionDocument() {}
    public static final String AGENT_NAME="法律意见书助手";
    public static final String INPUT_MARKER="【法律意见书案情输入 v1】";
    public static final List<String> KEYS=List.of("timeline","issues","analysis","risks","strategy","references");
    public static final List<String> TITLES=List.of("案件事实时间线","争议焦点","法律分析","风险与可行性","策略建议","引用清单");
    public static final String PROMPT=loadPrompt();
    private static String loadPrompt() {
        try(var input=LegalOpinionDocument.class.getResourceAsStream("/prompts/legal-opinion-system.txt")) {
            if(input==null)throw new IllegalStateException("Missing legal opinion prompt");
            return new String(input.readAllBytes(),StandardCharsets.UTF_8).strip();
        }catch(IOException e){throw new IllegalStateException("Cannot read legal opinion prompt",e);}
    }
    public static String agentPrompt(String name,String configured) {
        if(!AGENT_NAME.equals(name))return configured;
        return configured!=null&&configured.endsWith(PROMPT)?configured:(configured==null?"":configured+"\n")+PROMPT;
    }
    public record Section(String key,String title,String content,List<LegalReference> citations) {}
    public record Document(String title,List<Section> sections) {}
    private static String text(JsonNode n){return n!=null&&n.isTextual()?n.asText().strip():"";}
    private static ServiceException invalid(String message){return new ServiceException(message,400);}
    public static Document parse(String raw) {
        if(raw==null||raw.isBlank()||raw.length()>200000)throw invalid("意见书内容为空或超过20万字");
        String source=raw.strip();if(source.startsWith("```json")||source.startsWith("```")) {
            int newline=source.indexOf('\n');if(newline>0&&source.endsWith("```"))source=source.substring(newline+1,source.length()-3).strip();
        }
        JsonNode root;
        try { var mapper=new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);root=mapper.readTree(source); }
        catch(Exception e){throw invalid("意见书不是完整 JSON，请重新生成");}
        if(root==null||!root.isObject()||!root.path("sections").isArray()||root.path("sections").size()!=6)throw invalid("意见书必须完整包含六个章节");
        Map<String,Section> sections=new HashMap<>();Map<String,LegalReference> references=new LinkedHashMap<>();
        for(var section:root.path("sections")) {
            String key=text(section.get("key")),content=text(section.get("content"));
            if(!KEYS.contains(key)||sections.containsKey(key)||content.isBlank()||content.length()>60000)throw invalid("意见书章节缺失、重复或正文无效");
            List<LegalReference> citations=new ArrayList<>();JsonNode values=section.get("citations");
            if(values!=null&&!values.isArray())throw invalid("意见书引用清单必须为数组");
            if(values!=null){if(values.size()>100)throw invalid("单章节引用不能超过100条");for(var value:values){var reference=reference(value);citations.add(reference);references.putIfAbsent(reference.toJson(),reference);}}
            sections.put(key,new Section(key,TITLES.get(KEYS.indexOf(key)),content,List.copyOf(citations)));
        }
        var all=new ArrayList<Section>();for(String key:KEYS) {
            var section=sections.get(key);if(section==null)throw invalid("意见书缺少必要章节");
            if(key.equals("references"))section=new Section(key,section.title(),references.isEmpty()&&!section.content().contains("建议进一步检索")?section.content()+"\n\n知识库暂无匹配，建议进一步检索。":section.content(),List.copyOf(references.values()));
            all.add(section);
        }
        String title=text(root.get("title"));if(title.length()>200)throw invalid("意见书标题不能超过200字");
        return new Document(title.isEmpty()?"法律意见书":title,List.copyOf(all));
    }
    private static LegalReference reference(JsonNode value) {
        if(!value.isObject())throw invalid("引用必须使用法规/类案元数据对象");
        String type=text(value.get("type")),law=text(value.get("lawName")),article=text(value.get("articleNo")),caseNo=text(value.get("caseNo"));
        if((!type.equals("law")&&!type.equals("case"))||(type.equals("law")&&(law.isBlank()||article.isBlank()))||(type.equals("case")&&caseNo.isBlank()))throw invalid("引用缺少法规名称、条号或真实案号");
        String url=text(value.get("sourceUrl"));if(!url.isEmpty()&&!url.matches("(?i)^https?://[^\\s]+$"))throw invalid("引用来源地址无效");
        for(String field:List.of("lawName","articleNo","caseNo","title","court","status","sourceUrl","fragmentId"))if(text(value.get(field)).length()>2000)throw invalid("引用元数据过长");
        return new LegalReference(type,law,article,text(value.get("status")),caseNo,text(value.get("title")),text(value.get("court")),url,text(value.get("fragmentId")));
    }
    public static String label(LegalReference r) {
        if("case".equals(r.type()))return String.join(" · ",java.util.stream.Stream.of(r.caseNo(),r.title(),r.court()).filter(s->s!=null&&!s.isBlank()).toList());
        String article=r.articleNo().startsWith("第")?r.articleNo():"第"+r.articleNo().replaceAll("条$","")+"条";
        String warning=switch(r.status()==null?"":r.status()){case "effective"->"";case "repealed"->"（已失效）";case "amended"->"（已修改）";default->"（时效待核实）";};
        return "《"+r.lawName().replace("《","").replace("》","")+"》"+article+warning;
    }
    public static String markdown(Document document) {
        var out=new StringBuilder("# "+document.title()+"\n\n");int index=0;
        for(var section:document.sections()) {
            out.append("## ").append(++index).append(". ").append(section.title()).append("\n\n").append(section.content()).append("\n\n");
            for(var ref:section.citations()) {
                out.append("- ").append(label(ref));if(ref.sourceUrl()!=null&&!ref.sourceUrl().isBlank())out.append(" · 来源：").append(ref.sourceUrl());
                if(ref.fragmentId()!=null&&!ref.fragmentId().isBlank())out.append(" · 站内片段：").append(ref.fragmentId());out.append('\n');
            }
            out.append('\n');
        }
        return out.toString();
    }
    public static byte[] word(Document document) {
        try(var doc=new XWPFDocument();var out=new ByteArrayOutputStream()) {
            var section=doc.getDocument().getBody().addNewSectPr();var size=section.addNewPgSz();size.setW(11906);size.setH(16838);
            var margins=section.addNewPgMar();margins.setTop(1200);margins.setBottom(1200);margins.setLeft(1300);margins.setRight(1300);
            paragraph(doc,document.title(),20,true,null).setAlignment(ParagraphAlignment.CENTER);
            paragraph(doc,"导出日期："+LocalDate.now(ZoneId.of("Asia/Shanghai")),10,false,null);
            int index=0;
            for(var chapter:document.sections()) {
                paragraph(doc,++index+". "+chapter.title(),15,true,null).setKeepNext(true);
                for(String line:chapter.content().split("\\R",-1)) {
                    boolean heading=line.matches("^#{1,6}\\s+.*");
                    String plain=line.replaceFirst("^#{1,6}\\s+","").replace("**","").replaceFirst("^[-*]\\s+","• ");
                    paragraph(doc,plain,heading?12:11,heading,null);
                }
                for(var ref:chapter.citations()) {
                    String warning="law".equals(ref.type())&&!"effective".equals(ref.status())?("repealed".equals(ref.status())?"B42318":"9A4B00"):null;
                    paragraph(doc,label(ref),10,true,warning);
                    if(ref.sourceUrl()!=null&&!ref.sourceUrl().isBlank()) { var p=doc.createParagraph();var link=p.createHyperlinkRun(ref.sourceUrl());link.setText("原文来源："+ref.sourceUrl());link.setFontSize(10);link.setColor("245D91"); }
                    if(ref.fragmentId()!=null&&!ref.fragmentId().isBlank())paragraph(doc,"站内片段："+ref.fragmentId(),10,false,null);
                }
            }
            doc.write(out);return out.toByteArray();
        }catch(Exception e){throw new ServiceException("意见书 Word 导出失败，请重试",500);}
    }
    private static XWPFParagraph paragraph(XWPFDocument doc,String text,int size,boolean bold,String color) {
        var p=doc.createParagraph();p.setSpacingAfter(120);p.setSpacingBetween(1.5);
        var run=p.createRun();run.setFontFamily("微软雅黑");run.setFontSize(size);run.setBold(bold);if(color!=null)run.setColor(color);run.setText(text);return p;
    }
}
