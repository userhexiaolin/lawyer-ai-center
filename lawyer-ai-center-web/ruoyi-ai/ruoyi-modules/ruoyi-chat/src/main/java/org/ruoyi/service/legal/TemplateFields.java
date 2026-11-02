package org.ruoyi.service.legal;

import java.util.*;
import java.util.regex.*;

/** Existing blanks and server-identified standalone form captions may receive values. */
public final class TemplateFields {
    private TemplateFields(){}
    private static final Pattern SLOTS=Pattern.compile("\\{\\{[^{}]+}}|[_＿]{2,}|[ \\t　]{2,}|^[ \\t　]+|[ \\t　]+$|[ \\t　]+(?=[年月日元])|(?<=[：:])$");
    private static final Pattern ADDED_NOTES=Pattern.compile("【[^】]*(?:待补充|待确认|待核实)[^】]*】");
    private static final Pattern CAPTION=Pattern.compile("[ \\t　]*(致|收件人|委托人|发函方|姓名|地址|电话|联系电话|律师电话|律师联系电话|承办律师|经办律师|律师姓名|合同编号|函号|账号|发函日期|核算截止日|逾期起算日|催告届满日)[ \\t　]*[：:][ \\t　]*");
    public record Field(String id,String blockId,int start,int end,String original,String label,String kind,String unit,String presentation,boolean enabled,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) String blank){
        public Field(String id,String blockId,int start,int end,String original,String label,String kind,String unit,String presentation,boolean enabled){this(id,blockId,start,end,original,label,kind,unit,presentation,enabled,null);}
    }
    /** A whole form caption is an insertion anchor; a colon inside prose is not. */
    static String captionLabel(String text){
        var match=CAPTION.matcher(Objects.toString(text,""));
        if(!match.matches())return "";
        return "致".equals(match.group(1))?"收件人":match.group(1);
    }
    private static boolean captionAnchor(String text,int offset){return offset==text.length()&&!captionLabel(text).isEmpty();}
    /** Source values include full names; immutable template role suffixes are rendered once. */
    static String renderValue(String label,String fixedSuffix,String value){
        if(value==null)return null;
        if(Set.of("委托人","发函方").contains(label)&&fixedSuffix.startsWith("行")&&value.endsWith("行"))return value.substring(0,value.length()-1);
        if(label.matches("(?:承办|经办)?律师(?:姓名)?")&&fixedSuffix.startsWith("律师")&&value.endsWith("律师"))return value.substring(0,value.length()-2);
        return value;
    }
    public static List<int[]> ranges(TemplateLayout.Block block){
        return block.fields()==null?ranges(block.text()):block.fields().stream().filter(Field::enabled).map(f->new int[]{f.start(),f.end()}).toList();
    }
    public static List<int[]> ranges(String original){
        List<int[]> result=new ArrayList<>();var matcher=SLOTS.matcher(original);
        while(matcher.find())result.add(new int[]{matcher.start(),matcher.end()});return result;
    }
    public static List<String> values(String original,String replacement){
        return values(original,replacement,ranges(original));
    }
    public static List<String> values(TemplateLayout.Block original,String replacement){
        var result=values(original.text(),replacement,ranges(original));
        if(original.fields()!=null){int i=0;for(var f:original.fields())if(f.enabled()){
            String value=result.get(i++);if(value.equals(f.original())||value.equals("{{"+f.label()+"}}")||value.isBlank()||value.matches("[_＿]+"))continue;
            if((f.kind().equals("money")||f.kind().equals("number"))&&!value.matches("[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?"))throw new DraftingException("字段“"+f.label()+"”只能填写数值，不得改动或重复固定单位",422);
            if(f.kind().equals("date")&&!value.matches("[0-9]{4}(?:年[0-9]{1,2}月[0-9]{1,2}日|-[0-9]{2}-[0-9]{2})"))throw new DraftingException("字段“"+f.label()+"”须填写完整日期",422);
        }}return result;
    }
    /** Empty edits retain the original logical field instead of becoming a fabricated value. */
    public static String restoreBlanks(TemplateLayout.Block original,String replacement){
        var ranges=ranges(original);var entries=values(original,replacement);var text=new StringBuilder();int position=0;
        for(int i=0;i<ranges.size();i++){
            var range=ranges.get(i);String value=entries.get(i);
            text.append(original.text(),position,range[0]).append(value.isBlank()||value.matches("[_＿]+")?original.text().substring(range[0],range[1]):value);
            position=range[1];
        }
        return text.append(original.text().substring(position)).toString();
    }
    private static List<String> values(String original,String replacement,List<int[]> ranges){
        StringBuilder expression=new StringBuilder("(?s)^");int position=0;
        for(var range:ranges){expression.append(Pattern.quote(original.substring(position,range[0]))).append('(');
            if(range[1]>range[0])expression.append(Pattern.quote(original.substring(range[0],range[1]))).append('|');
            expression.append(".*?)");position=range[1];}
        var match=Pattern.compile(expression.append(Pattern.quote(original.substring(position))).append('$').toString()).matcher(replacement);
        if(!match.matches())throw new DraftingException("填写内容修改了模板原文；只能填写模板已有空白或占位符，不能改写固定文字",422);
        var notes=ADDED_NOTES.matcher(replacement);while(notes.find())if(!original.contains(notes.group()))throw new DraftingException("不得向模板额外添加待补充或待确认文字；资料缺失时保留原有空白或占位符",422);
        List<String> values=new ArrayList<>();for(int i=1;i<=ranges.size();i++)values.add(match.group(i));return values;
    }
    public static Field candidate(String block,int start,int end,String text,String previous,boolean underlined){
        String raw=text.substring(start,end),left=text.substring(0,start),right=text.substring(end);
        String label=raw.startsWith("{{")?raw.substring(2,raw.length()-2).strip():raw.isEmpty()&&captionAnchor(text,start)?captionLabel(text):"";
        if(label.isEmpty()){
            var m=Pattern.compile("(合同编号|函号|本次催收总额|本息合计|逾期本金|贷款本金|本金|正常利息|利息|罚息|收件人|承办律师|律师姓名|律师电话|联系电话|电话|发函日期|催告届满日|核算截止日|姓名|地址|账号)[：: 为]*$").matcher(left);
            if(m.find())label=m.group(1);
            else if(left.matches("(?s).*接受[：: ]*$"))label="委托人";
            else if(left.matches("\\s*致[：:]\\s*"))label="收件人";
            else if(left.isBlank()&&right.matches("^(?:先生/女士|先生|女士)[：:].*"))label="收件人";
            else if(left.matches("(?s).*(?:专职|承办|经办)律师[：: ]*$"))label="承办律师";
            else if(right.matches("^律师.*")&&left.isBlank())label="承办律师";
            else if(!right.isEmpty()&&"年月日".indexOf(right.charAt(0))>=0){
                String date=left.matches("(?s).*(?:截至|截止|核算).*")?"核算截止日":left.matches("(?s).*(?:贷款自|逾期自|逾期起算).*")?"逾期起算日":"发函日期";
                label=date+"（"+right.charAt(0)+"）";
            }
            else label="填写项";
        }
        String unit=right.startsWith("元")?"元":right.startsWith("年")?"年":right.startsWith("月")?"月":right.startsWith("日")?"日":right.startsWith("%")?"%":"";
        String kind=unit.equals("元")||label.matches(".*(?:本金|利息|罚息|合计|总额|金额).*")?"money":label.matches(".*(?:日期|起算日|截止日|届满日).*")?(unit.matches("[年月日]")?"number":"date"):"text";
        boolean enabled=!text.isBlank()&&!(left.isBlank()&&right.isBlank())&&!(left.isBlank()&&raw.isBlank()&&label.equals("填写项"));
        return new Field(block+"-"+start+"-"+end,block,start,end,raw,label,kind,unit,underlined||raw.matches("[_＿]+")?"underline":"text",enabled);
    }
    /** Maps are sidecar metadata: the original text is never rewritten or persisted with markers. */
    public static List<TemplateLayout.Block> logical(List<TemplateLayout.Block> originals){
        List<TemplateLayout.Block> result=new ArrayList<>();
        for(var b:originals){
            if(b.fields()==null){result.add(b);continue;}
            StringBuilder text=new StringBuilder();List<Field> mapped=new ArrayList<>();int position=0;
            for(var f:b.fields()){
                text.append(b.text(),position,f.start());int start=text.length();
                String value=f.enabled()?"{{"+f.label()+"}}":b.text().substring(f.start(),f.end());text.append(value);
                mapped.add(new Field(f.id(),b.id(),start,text.length(),value,f.label(),f.kind(),f.unit(),f.presentation(),f.enabled(),f.original()));position=f.end();
            }
            text.append(b.text().substring(position));result.add(new TemplateLayout.Block(b.id(),text.toString(),b.editable(),mapped));
        }
        return result;
    }
    public static String displayBlank(Field field){
        String original=field.blank()==null?field.original():field.blank();
        if(original.matches("\\{\\{[^{}]+}}"))return "underline".equals(field.presentation())?"_".repeat(original.length()):" ".repeat(original.length());
        if("underline".equals(field.presentation())&&original.isBlank())return "_".repeat(Math.max(1,original.length()));
        return original;
    }
    public static String displayText(TemplateLayout.Block block){
        String value=block.text();
        if(block.fields()!=null)for(int i=block.fields().size()-1;i>=0;i--){var f=block.fields().get(i);String entry=value.substring(f.start(),f.end());
            if(entry.equals("{{"+f.label()+"}}"))value=value.substring(0,f.start())+displayBlank(f)+value.substring(f.end());
        }
        return TemplateLayout.presentBlanks(value);
    }
    /** Positions in the current draft, with native blank text/style from the trusted template. */
    public static List<TemplateLayout.Block> presentation(List<TemplateLayout.Block> original,List<TemplateLayout.Block> filled){
        Map<String,TemplateLayout.Block> sources=new HashMap<>();original.forEach(b->sources.put(b.id(),b));List<TemplateLayout.Block> result=new ArrayList<>();
        for(var b:filled){var source=sources.get(b.id());
            if(source==null||source.fields()==null){result.add(new TemplateLayout.Block(b.id(),b.text(),b.editable()));continue;}
            var entries=values(source,b.text());List<Field> fields=new ArrayList<>();int position=0,current=0,index=0;
            for(var f:source.fields())if(f.enabled()){
                current+=f.start()-position;String value=entries.get(index++);
                fields.add(new Field(f.id(),b.id(),current,current+value.length(),f.original(),f.label(),f.kind(),f.unit(),f.presentation(),true,f.blank()));
                current+=value.length();position=f.end();
            }
            result.add(new TemplateLayout.Block(b.id(),b.text(),b.editable(),fields));
        }
        return result;
    }
    public static List<Field> validate(List<TemplateLayout.Block> blocks,List<Field> fields){
        if(fields==null||fields.size()>500)throw new DraftingException("请核对模板填写字段，最多500项",400);
        Map<String,TemplateLayout.Block> originals=new HashMap<>();blocks.forEach(b->originals.put(b.id(),b));
        Set<String> ids=new HashSet<>(),locations=new HashSet<>();Map<String,Integer> ends=new HashMap<>();List<Field> checked=new ArrayList<>();
        for(var f:fields){
            var b=f==null?null:originals.get(f.blockId());
            if(b==null||f.id()==null||!ids.add(f.id())||!locations.add(f.blockId()+":"+f.start()+":"+f.end())||f.start()<ends.getOrDefault(f.blockId(),0)||f.end()<f.start()||f.end()>b.text().length())throw new DraftingException("字段位置无效、重复或重叠，请重新识别",400);
            var candidate=b.fields()==null?null:b.fields().stream().filter(c->c.start()==f.start()&&c.end()==f.end()).findFirst().orElse(null);
            if(f.start()==f.end()&&!(candidate!=null&&Objects.equals(candidate.id(),f.id())&&Objects.equals(candidate.blockId(),b.id())&&"".equals(candidate.original())
                &&((f.start()==0&&b.text().isEmpty())||captionAnchor(b.text(),f.start()))))throw new DraftingException("填写位置须对应原件空白或服务端识别的独立标签，请重新识别；不能在正文冒号后新增位置",400);
            String fragment=b.text().substring(f.start(),f.end());
            if(!fragment.equals(f.original())||!(fragment.matches("[_＿\\s　]*")||fragment.matches("\\{\\{[^{}]+}}")))throw new DraftingException("字段只能标记原有空白，不得覆盖模板固定文字或单位",400);
            if(f.label()==null||f.label().isBlank()||f.label().length()>80||f.label().matches("(?s).*[{}\\r\\n].*")||!Set.of("text","money","date","number").contains(f.kind())||!Set.of("text","underline").contains(f.presentation()))throw new DraftingException("请填写有效字段名称、类型及显示方式",400);
            if(candidate!=null&&(!Objects.equals(candidate.unit(),f.unit())||!Objects.equals(candidate.presentation(),f.presentation())))throw new DraftingException("字段单位和下划线样式必须保留原模板设置",400);
            ends.put(f.blockId(),f.end());checked.add(f);
        }
        return checked;
    }
}
