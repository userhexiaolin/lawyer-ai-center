package org.ruoyi.service.legal;

import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class DocumentExport {
    private final TemplateFiles files;private final DocumentWord word;private final DocumentConversion conversion;
    public DocumentExport(TemplateFiles files,DocumentWord word,DocumentConversion conversion){this.files=files;this.word=word;this.conversion=conversion;}
    public record File(byte[] bytes,String mime,String extension){}
    public String describeDraft(String raw,String request){
        var document=TemplateDrafting.read(raw);var binding=TemplateDrafting.expected(request);
        if(document==null||binding==null||!binding.documentTemplate().equals(document.documentTemplate()))return raw;
        try{
            var version=files.load(binding.documentTemplate());
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(new TemplateDrafting.Document(document.documentTemplate(),TemplateFields.presentation(TemplateFields.logical(version.blocks()),document.blocks()),document.calculations(),document.calculationResults(),document.fillReport()));
        }catch(Exception ignored){return raw;} // An invalid historical draft must remain recoverable.
    }
    public String checkedDraft(String raw,String request){
        var document=TemplateDrafting.read(raw);var binding=TemplateDrafting.expected(request);
        if(document==null||binding==null||!binding.documentTemplate().equals(document.documentTemplate()))throw new DraftingException("草稿与模板版本不一致，请恢复该草稿后重试",422);
        var version=files.load(binding.documentTemplate());TemplateDrafting.requireCompatible(request,version.blocks());
        String checked=TemplateDrafting.finish(raw,validatedRequest(request,version),true);
        return checked;
    }
    public File generate(String title,String raw,String format,String request){
        return generate(title,raw,format,request,"template");
    }
    public File generate(String title,String raw,String format,String request,String layout){
        if(!Set.of("template","standard").contains(Objects.toString(layout,"")))throw new DraftingException("请选择原模板版式或标准版式",400);
        boolean standard="standard".equals(layout);
        if(!Set.of("docx","pdf","md","txt").contains(Objects.toString(format,"")))throw new DraftingException("请选择Word、PDF、Markdown或TXT格式",400);
        String content=DocumentWord.normalize(raw);var document=TemplateDrafting.read(content);byte[] docx=null;String text;
        if(document!=null){
            var binding=TemplateDrafting.expected(request);
            if(binding==null||!binding.documentTemplate().equals(document.documentTemplate()))throw new DraftingException("草稿与模板版本不一致，请恢复该草稿后重试",422);
            var version=files.load(binding.documentTemplate());
            TemplateDrafting.requireCompatible(request,version.blocks());
            // Revalidate edits against the immutable source, never trust IDs or fixed text from the browser/history.
            content=TemplateDrafting.finish(content,validatedRequest(request,version),true);document=TemplateDrafting.read(content);
            // Completeness remains in the draft report, never an export permission check.
            // Keep canonical markers in saved data and sanitize only the output copy.
            text=document.blocks().stream().map(TemplateFields::displayText).collect(java.util.stream.Collectors.joining("\n\n"));
            if(!format.equals("txt"))docx=TemplateLayout.presentBlanks(standard?word.generateTemplate(text):TemplateLayout.fill(version.docx(),document.blocks(),version.blocks()));
            if(format.equals("md")&&!standard)text=TemplateLayout.markdown(docx);
        }else{
            if(TemplateDrafting.applies(request))throw new DraftingException("模板草稿填写区域已损坏，无法按原版式导出，请恢复草稿后重试",422);
            if(!standard&&request!=null&&request.contains("---律所模板开始---")&&(format.equals("docx")||format.equals("pdf")))throw new DraftingException("该草稿使用的历史模板仅保存了正文，无法还原原模板版式。可选择“按标准版式下载当前正文”；若需原模板字体和排版，请在模板管理中补传一次原文件后重新起草",422);
            DraftingFacts.requireGrounded(content,request,true);
            text=TemplateLayout.presentBlanks(content);
            if(format.equals("docx")||format.equals("pdf"))docx=word.generate(title,text);
        }
        return switch(format){
            case "docx"->new File(docx,"application/vnd.openxmlformats-officedocument.wordprocessingml.document","docx");
            case "pdf"->new File(conversion.convert(docx,"docx","pdf"),"application/pdf","pdf");
            case "md"->new File(text.getBytes(StandardCharsets.UTF_8),"text/markdown;charset=UTF-8","md");
            default->new File(plain(text).getBytes(StandardCharsets.UTF_8),"text/plain;charset=UTF-8","txt");
        };
    }
    static String plain(String text){return text.replaceAll("(?m)^#{1,6}\\s+","").replace("**","").replaceAll("(?m)^>\\s?","").replaceAll("\\[([^]\\n]+)]\\(([^)]+)\\)","$1 ($2)");}
    private static String validatedRequest(String request,TemplateFiles.Version version){
        // Bind fresh immutable slots while retaining the owned session's source for re-computation.
        return request.replaceAll("(?s)---版式填写JSON开始---.*?---版式填写JSON结束---",java.util.regex.Matcher.quoteReplacement(TemplateDrafting.prompt(version).strip()));
    }
}
