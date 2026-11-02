package org.ruoyi.service.legal;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;

/** Fills recognizable slots from explicit, unambiguous source labels, never from loan assumptions. */
final class TemplateSourceValues {
    private TemplateSourceValues() {}
    private static final String DATE="(\\d{4}\\s*年\\s*\\d{1,2}\\s*月\\s*\\d{1,2}\\s*日)";
    private static final String AMOUNT="([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)\\s*元";
    private static final String SEP="[\\s：:为]*";
    private static final String HORIZONTAL="[\\p{Zs}\\t]*";
    private static final String ENTITY_LABEL="委托人(?:及发函方)?|发函方|收件人|(?:承办|经办)律师(?:姓名)?|律师姓名|律师(?:联系)?电话|联系电话|电话|合同编号|发函日期|核算截止日";
    private static final Pattern MISSING_VALUE=Pattern.compile("待(?:补充|确认|核实|确定|填写|提供|指定|补齐)|未(?:提供|指定|明确|确认|确定|填写|记载|披露)|尚未(?:提供|指定|明确|确认|确定)|不详|未知|待定|暂无|(?i:^(?:n/?a|tbd|null)$)");
    private static final Pattern LABEL_ONLY=Pattern.compile("(?:"+ENTITY_LABEL+")[：:]?|[\\p{L}\\p{N}_（）()【】\\[\\]/／、，, \\t　-]{1,80}[：:]");
    private static final Pattern NEXT_ENTITY_LABEL=Pattern.compile("(?:^|[\\p{Zs}\\t，,]+)(?:"+ENTITY_LABEL+")[：:]");
    static final String DEADLINE_LABEL="(?:催告(?:履行)?期限[^。\\n]{0,180}?(?:截止(?:至)?|届满日[：:]?)|催告届满日|催告截止日)";
    /** Labels, placeholders and missing-value markers are never source facts. */
    static boolean meaningfulValue(String value) {
        if(value==null)return false;
        String clean=value.strip();
        if(clean.isBlank()||MISSING_VALUE.matcher(clean).find()||LABEL_ONLY.matcher(clean).matches())return false;
        if(clean.contains("{{")||clean.contains("}}")||clean.matches("[\\s　_＿—–.·•…:：;；,，。-]+"))return false;
        return true;
    }
    private static String unique(String source,String expression,boolean money) {
        return unique(source,expression,money,null);
    }
    private static String entity(String source,String expression) {
        return unique(source,expression,false,NEXT_ENTITY_LABEL);
    }
    private static String unique(String source,String expression,boolean money,Pattern nextLabel) {
        Set<String> values=new LinkedHashSet<>();var match=Pattern.compile(expression).matcher(source);
        while(match.find()) {
            String prefix=source.substring(Math.max(0,match.start()-80),match.start()).replaceFirst("(?s)^.*[。；;\\n]","");
            if(prefix.matches("(?s).*(?:未提供|未明确|未指定|并非|不是|不得|不能|不采用).*"))continue;
            String value=match.group(1).strip();
            if(nextLabel!=null){
                // Empty labels may be adjacent on one line as well as separate lines.
                var next=nextLabel.matcher(value);
                if(next.find())value=value.substring(0,next.start()).replaceFirst("[\\p{Zs}\\t，,]+$","").strip();
            }
            if(!meaningfulValue(value))continue;
            if(money)value=new BigDecimal(value.replace(",","")).setScale(2).toPlainString();
            else if(value.matches("(?s)\\d{4}\\s*年.*"))value=value.replaceAll("\\s","");
            values.add(value);
        }
        return values.size()==1?values.iterator().next():null;
    }
    static Map<String,String> read(String request) {
        var factors=DraftingFacts.input(request);if(factors==null)return Map.of();
        String source=String.join("\n",factors.parties(),factors.facts(),factors.purpose(),factors.deadline());
        Map<String,String> result=new HashMap<>();
        put(result,"client",entity(source,"(?:委托人(?:及发函方)?|发函方)[：:]"+HORIZONTAL+"([^；;。\\r\\n]+)"));
        put(result,"recipient",entity(source,"收件人[：:]"+HORIZONTAL+"([^；;。\\r\\n]+)"));
        put(result,"lawyer",entity(source,"(?:承办|经办)律师(?:姓名)?[：:]"+HORIZONTAL+"([^，,；;。\\r\\n]+)"));
        put(result,"lawyerPhone",entity(source,"(?:(?:承办|经办)律师[^\\r\\n。]{0,200}?(?:联系电话|电话)|律师联系电话|律师电话)[：:]"+HORIZONTAL+"([+0-9()（） -]{3,40})"));
        put(result,"contractNo",entity(source,"(?m)^"+HORIZONTAL+"合同编号[：: \\t　]*([^\\r\\n，,。；;]+)"));
        put(result,"overdueDate",unique(source,"(?:首期逾期起算日|逾期起算日|逾期开始日期|逾期起始日)"+SEP+DATE,false));
        // A payment deadline is not an accounting cutoff. Explicit accounting labels
        // remain authoritative even when another sentence says "截止...支付".
        String cutoffLabels="(?:核对截止日|核算截止日|欠款截止日)";
        String cutoff=unique(source,cutoffLabels+SEP+DATE,false);
        if(!Pattern.compile(cutoffLabels+SEP+DATE).matcher(source).find())
            cutoff=unique(source,"(?:截至|截止)"+SEP+DATE,false);
        put(result,"cutoffDate",cutoff);
        put(result,"letterDate",unique(source,"(?:拟定)?发函日期"+SEP+DATE,false));
        put(result,"principal",totalOrSingle(source,"(?:已到期(?:逾期)?本金|逾期本金)"));
        put(result,"interest",totalOrSingle(source,"(?:已到期正常利息|未付正常利息|逾期正常利息)"));
        put(result,"total",unique(source,"本息合计"+SEP+AMOUNT,true));
        put(result,"penalty",totalOrSingle(source,"(?:逾期罚息|罚息)"));
        put(result,"claimTotal",unique(source,"本次催收总额"+SEP+AMOUNT,true));
        put(result,"remainingPrincipal",unique(source,"剩余本金"+SEP+AMOUNT,true));
        put(result,"demandDate",unique(source,DEADLINE_LABEL+SEP+DATE,false));
        readTextFields(request,source,result);
        return result;
    }
    /** Explicit source labels support new text fields without adding template-specific aliases. */
    private static void readTextFields(String request,String source,Map<String,String> result) {
        var document=TemplateDrafting.expected(request);
        if(document==null||document.blocks()==null)return;
        Set<String> labels=new LinkedHashSet<>(),textLabels=new LinkedHashSet<>();
        for(var block:document.blocks())if(block!=null&&block.editable()&&block.fields()!=null)
            for(var field:block.fields())if(field!=null&&field.enabled()&&field.label()!=null&&!field.label().isBlank()){
                String label=field.label().strip();
                labels.add(label);
                if("text".equals(field.kind()))textLabels.add(label);
            }
        if(textLabels.isEmpty())return;
        // Every enabled field label is a boundary, even when its value belongs to the
        // separate money/date/number calculation path. Never read the template as facts.
        String alternatives=labels.stream().sorted(Comparator.comparingInt(String::length).reversed())
            .map(Pattern::quote).collect(java.util.stream.Collectors.joining("|"));
        Pattern nextLabel=Pattern.compile("(?:"+ENTITY_LABEL+"|"+alternatives+")[：:]");
        for(String label:textLabels){
            String expression="(?<![\\p{L}\\p{N}_])"+Pattern.quote(label)+"[：:]"+HORIZONTAL+"([^；;。\\r\\n]+)";
            String value=unique(source,expression,false,nextLabel);
            if(value!=null&&!labels.contains(value))put(result,"field:"+label,value);
        }
    }
    private static void put(Map<String,String> values,String key,String value) {if(value!=null)values.put(key,value);}
    private static String totalOrSingle(String source,String label){
        String aggregate=label+"合计"+SEP+AMOUNT;
        if(Pattern.compile(aggregate).matcher(source).find())return unique(source,aggregate,true);
        return unique(source,label+SEP+AMOUNT,true);
    }
    private static String datePart(String date,char unit) {
        if(date==null)return null;var match=Pattern.compile("(\\d+)"+unit).matcher(date);
        return match.find()?String.valueOf(Integer.parseInt(match.group(1))):null;
    }
    private static boolean sameSourceValue(String first,String second) {
        String a=first.strip(),b=second.strip();
        if(a.equals(b))return true;
        try{return new BigDecimal(a.replace(",","")).compareTo(new BigDecimal(b.replace(",","")))==0;}
        catch(NumberFormatException ignored){}
        var left=Pattern.compile(DATE).matcher(a);var right=Pattern.compile(DATE).matcher(b);
        if(left.matches()&&right.matches())
            return Objects.equals(datePart(a,'年'),datePart(b,'年'))&&Objects.equals(datePart(a,'月'),datePart(b,'月'))&&Objects.equals(datePart(a,'日'),datePart(b,'日'));
        return false;
    }
    private static String named(String label,Map<String,String> values) {
        var part=Pattern.compile("(.+)（([年月日])）").matcher(label.strip());
        if(part.matches())return datePart(named(part.group(1),values),part.group(2).charAt(0));
        return switch(label.strip()) {
            case "委托人","发函方" -> values.get("client");
            case "收件人" -> values.get("recipient");
            case "律师","承办律师","承办律师姓名","律师姓名" -> values.get("lawyer");
            case "律师电话","承办律师电话","律师联系电话","联系电话","电话" -> values.get("lawyerPhone");
            case "合同编号" -> values.get("contractNo");
            case "逾期本金","到期本金","贷款本金","本金","欠款本金" -> values.get("principal");
            case "到期利息","正常利息","逾期利息","利息" -> values.get("interest");
            case "本息合计","到期本息" -> values.get("total");
            case "罚息","逾期罚息" -> values.get("penalty");
            case "本次催收总额" -> values.get("claimTotal");
            case "催告届满日","催告截止日" -> values.get("demandDate");
            case "逾期起算日","逾期起始日" -> values.get("overdueDate");
            case "核算截止日","截止日期","核对截止日" -> values.get("cutoffDate");
            case "发函日期" -> values.get("letterDate");
            default -> values.get("field:"+label.strip());
        };
    }
    static String fill(String original,String replacement,String previous,Map<String,String> values) {
        return fill(new TemplateLayout.Block("",original,true),replacement,previous,values);
    }
    static String fill(TemplateLayout.Block block,String replacement,String previous,Map<String,String> values) {
        String original=block.text();var ranges=TemplateFields.ranges(block);var insertions=TemplateFields.values(block,replacement);
        StringBuilder result=new StringBuilder();int position=0;
        for(int i=0;i<ranges.size();i++) {
            var range=ranges.get(i);String left=original.substring(0,range[0]),right=original.substring(range[1]),slot=original.substring(range[0],range[1]);
            String value=null;
            if(slot.startsWith("{{")){
                value=named(slot.substring(2,slot.length()-2),values);
                if(value!=null&&right.startsWith("行")&&value.endsWith("行")&&slot.contains("委托人"))value=value.substring(0,value.length()-1);
            }
            else if(left.matches("(?s).*接受\\s*$")) {
                value=values.get("client");
                // The trailing 行 belongs to the immutable template, not the inserted bank name.
                if(value!=null&&right.startsWith("行")&&value.endsWith("行"))value=value.substring(0,value.length()-1);
            }
            else if(left.matches("(?s).*(?:专职|承办|经办)律师\\s*$")||(range[0]==0&&original.matches("^[ \\t　_＿]+律师[ \\t　]*$")))value=values.get("lawyer");
            else if(left.matches("\\s*(?:致|收件人)[：:]\\s*$"))value=values.get("recipient");
            else if(left.matches("(?s).*合同编号为?\\s*$"))value=values.get("contractNo");
            else if(left.matches("(?s).*本息合计\\s*$"))value=values.get("total");
            else if(left.matches("(?s).*(?:拖欠|逾期|欠款).*本金\\s*$"))value=values.get("principal");
            else if(left.matches("(?s).*(?:拖欠|逾期|欠款).*利息\\s*$"))value=values.get("interest");
            else if(left.matches("\\s*(?:联系电话|电话)[：:]\\s*$")&&previous.matches("(?s).*(?:律师|律所|事务所).*"))value=values.get("lawyerPhone");
            else if(!right.isEmpty()&&"年月日".indexOf(right.charAt(0))>=0) {
                String key=null;
                if(left.matches("(?s).*(?:截止|截至).*"))key="cutoffDate";
                else if(left.matches("(?s).*(?:贷款自|逾期自|逾期起算日|逾期起始日).*"))key="overdueDate";
                else if(original.matches("\\s*\\d{4}年[ \\t　_＿]*月[ \\t　_＿]*日[ \\t　]*")||left.matches("(?s).*发函日期.*"))key="letterDate";
                if(key!=null) {
                    String date=values.get(key);
                    if(key.equals("letterDate")&&date!=null&&original.matches("\\s*\\d{4}年.*")&&!original.stripLeading().startsWith(datePart(date,'年')+"年"))
                        throw new DraftingException("发函日期与模板固定年份不一致，请更换模板或核对发函日期",422);
                    value=datePart(date,right.charAt(0));
                }
            }
            String existing=insertions.get(i);
            String label=slot.startsWith("{{")?slot.substring(2,slot.length()-2):TemplateFields.candidate(block.id(),range[0],range[1],original,previous,false).label();
            value=TemplateFields.renderValue(label,right,value);
            String renderedExisting=TemplateFields.renderValue(label,right,existing);
            if(value!=null&&!existing.equals(slot)&&meaningfulValue(existing)&&!sameSourceValue(renderedExisting,value))
                throw new DraftingException("模板字段的来源值与填写值冲突，请核对本次资料后重试",422);
            result.append(original,position,range[0]).append(value==null?existing:value);position=range[1];
        }
        return result.append(original.substring(position)).toString();
    }
}
