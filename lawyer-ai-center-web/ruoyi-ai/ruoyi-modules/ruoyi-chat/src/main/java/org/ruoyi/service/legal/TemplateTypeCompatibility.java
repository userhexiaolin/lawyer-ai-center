package org.ruoyi.service.legal;

import java.text.Normalizer;
import java.util.*;

/** Type inference is advisory for manual classification; strict validation is reserved for AI/output. */
final class TemplateTypeCompatibility {
    private TemplateTypeCompatibility() {}
    private static final List<String> FORMS = List.of("民事起诉状", "民事答辩状", "行政起诉状", "行政答辩状", "刑事自诉状", "仲裁申请书", "法律意见书", "授权委托书", "律师函", "起诉状", "答辩状", "催款函", "通知书", "告知书", "申请书", "证明书", "台账", "清单", "目录", "合同", "协议");

    private static String normalized(String value) {
        return Normalizer.normalize(Objects.toString(value,""),Normalizer.Form.NFKC).replaceAll("[\\s\\u3000\\uFEFF]","");
    }
    private static String core(String value) {
        String text=normalized(value).replaceFirst("(?i)\\.(?:docx?|pdf|txt|md|rtf|odt)$","");
        text=text.replaceFirst("^[#*`《【\\[\\d、._-]+","").replaceFirst("[》】\\]：:]+$","");
        for(int i=0;i<2;i++)text=text.replaceFirst("\\([^()]{0,40}\\)$","").replaceFirst("(?:通用)?(?:模板|范本|样本|示范文本)$","");
        return text;
    }
    private static String form(String value) {
        String text=core(value);
        if(text.equals("合同文书"))return "合同";
        for(String candidate:FORMS)if(text.endsWith(candidate))return switch(candidate){
            case "起诉状" -> "民事起诉状";
            case "答辩状" -> "民事答辩状";
            case "协议" -> "合同";
            default -> candidate;
        };
        return null;
    }
    static String detected(String text) {
        for(String line:Objects.toString(text,"").lines().filter(value->!value.isBlank()).limit(8).toList()){
            String heading=core(line);
            if(heading.length()>100||heading.matches(".*[，。！？；：:,!?;].*")
                ||heading.matches("^(?:根据|依据|本函|本合同|参考|引用).*"))continue;
            // A bundle of different forms is not a reliable single-form title.
            if(heading.matches(".*[及与、/&暨].*"))continue;
            String found=form(heading);if(found!=null)return found;
        }
        String body=normalized(text);
        if(body.matches("(?s).*(?:本所接受.{0,40}委托|律师事务所).*")
            &&body.matches("(?s).*(?:本函|特致此函|指派.{0,15}律师|承办律师).*"))return "律师函";
        return null;
    }
    static String error(String selected,String name,String content) {
        if("自由描述文书".equals(selected))return "";
        String actual=detected(content);
        if(actual==null)actual=detected(name);
        if(actual==null)return ""; // Lack of evidence is not a positive semantic match.
        String target=form(selected);
        if(actual.equals(target))return "";
        return "模板名称或正文表明文书为“"+actual+"”，与所选“"+selected+"”不匹配；请改选相符类型，修改类型不会改写原文。本次类型未保存";
    }
    static void require(String selected,String name,String content) {
        String error=error(selected,name,content);if(!error.isEmpty())throw new DraftingException(error,422);
    }
    static String advice(String selected,String name,String content) {
        if(error(selected,name,content).isEmpty())return "";
        String actual=detected(content);if(actual==null)actual=detected(name);
        return "辅助识别为“"+actual+"”，当前按人工设置的“"+selected+"”类型使用。识别结果仅供参考，不影响保存和起草。";
    }
}
