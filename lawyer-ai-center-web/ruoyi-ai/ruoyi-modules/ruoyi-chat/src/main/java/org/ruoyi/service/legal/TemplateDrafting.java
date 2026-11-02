package org.ruoyi.service.legal;

import com.fasterxml.jackson.databind.*;
import dev.langchain4j.model.chat.response.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.*;

/** A document carries stable block IDs so editing/export never guesses paragraph locations. */
public final class TemplateDrafting {
    private TemplateDrafting(){}
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final Pattern INPUT=Pattern.compile("---版式填写JSON开始---\\s*([\\s\\S]*?)\\s*---版式填写JSON结束---");
    private static final String DAY_COUNT="([0-9]{1,3}|[一二三四五六七八九]?十[一二三四五六七八九]?|[一二三四五六七八九])";
    public static final String SYSTEM_PROMPT="""
        严格按原模板填写，不得改写模板的任何固定文字、业务内容、标题、条款或署名。
        只填写editable=true段落中原有的双花括号占位符、下划线空白或连续空格留白。勾选整段并不授权改写整段。
        根据本次资料填入对应字段；合同约定不代表已经违约，租金、押金不能填作贷款或欠款。资料不匹配时不得强行代入。
        通读起草要素中的合同全文及全部附件，逐项核对每个空白。履行表中明确记载的逾期、欠款、催告及发函参数属于本次已提供资料，不得遗漏。
        区分贷款总额、全部剩余本金和本次到期逾期本金；本金、利息、合计、逾期起算日和核算截止日各填对应值。承办律师、律师电话、发函日期已明确提供时必须填写，不能误用银行电话、借款人电话或合同签约日。
        没有可靠资料的字段保留原模板的空白或占位符，不添加任何待补充、待确认、说明或提示文字，不虚构日期或律师信息。固定文字紧接空白时避免重复，例如模板固定“行”字，填入银行名称后只出现一个“行”。
        需要运算的金额、利息、天数及日期只声明calculations计算方案和原文依据，按本次计算协议交程序核算，不自行填入心算结果；calculations与blocks并列，计算结果不得冒充已人工复核。
        本次模板存在fields字段时，只输出JSON对象 {"documentTemplate":"本次版本ID","fieldValues":[{"fieldId":"已启用的字段ID","value":"不含固定单位的填写值","quote":"本次资料的逐字原文依据"}],"calculations":[]}，不输出blocks，不改写整段。
        没有fields的旧模板才输出 {"documentTemplate":"本次版本ID","blocks":[{"id":"段落ID","text":"填写后该段完整原文"}]}，逐一输出editable=true的所有段落；无可填写位置的段落逐字保持原文，保留空格和标点。
        不输出代码围栏或引用元数据。无来源的字段必须从fieldValues中省略整条记录，不返回空值、null、占位符、待补充文字或空quote；程序会保留原模板空白。每个fieldId最多出现一次，禁止新增字段ID。
        """;
    public record Document(String documentTemplate,List<TemplateLayout.Block> blocks,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY) List<DraftingCalculations.Plan> calculations,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY) List<DraftingCalculations.Result> calculationResults,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) TemplateFillReport.Report fillReport){
        public Document(String documentTemplate,List<TemplateLayout.Block> blocks){this(documentTemplate,blocks,null,null,null);}
        public Document(String documentTemplate,List<TemplateLayout.Block> blocks,List<DraftingCalculations.Plan> calculations,List<DraftingCalculations.Result> calculationResults){this(documentTemplate,blocks,calculations,calculationResults,null);}
    }
    public static boolean applies(String request){return request!=null&&INPUT.matcher(request).find();}
    public static String prompt(TemplateFiles.Version v){
        try{return "\n\n---版式填写JSON开始---\n"+JSON.writeValueAsString(new Document(v.id(),TemplateFields.logical(v.blocks())))+"\n---版式填写JSON结束---\n模板字段是独立映射，原文件未改写。必须按版式填写协议输出JSON，不输出全文Markdown。";}
        catch(Exception e){throw new DraftingException("模板填写区域无效",422);}
    }
    public static Document read(String content){
        try{JsonNode root=JSON.readTree(content);if(!root.isObject()||!root.has("documentTemplate"))return null;return JSON.treeToValue(root,Document.class);}
        catch(Exception ignored){return null;}
    }
    public static Document expected(String request){var match=INPUT.matcher(Objects.toString(request,""));if(!match.find())return null;Document d=read(match.group(1));if(d==null)throw new DraftingException("模板版本信息缺失，请重新生成",422);return d;}
    public static void requireCompatible(String request,List<TemplateLayout.Block> blocks){
        var factors=DraftingFacts.input(request);if(factors==null)return;
        String facts=factors.facts(),template=blocks.stream().map(TemplateLayout.Block::text).reduce("",(a,b)->a+"\n"+b);
        if(facts.contains("租赁")&&!facts.matches("(?s).*(?:贷款|按揭).*")&&template.matches("(?s).*(?:按揭|个人贷款|贷款本金|拖欠我行).*"))
            throw new DraftingException("所导入的是租赁合同，当前模板为贷款或按揭还款催收函，业务内容不匹配。严格按模板填充时不能改写为租金催收函，请更换匹配的合同或模板",422);
        TemplateBusinessCompatibility.require(factors,blocks);
        requireCompatibleDeadline(factors,template);
        String date=TemplateSourceValues.read(request).get("letterDate");
        if(date!=null)for(var block:blocks){var year=Pattern.compile("^\\s*(\\d{4})年.*月.*日\\s*$").matcher(block.text());
            if(year.matches()&&!date.startsWith(year.group(1)+"年"))throw new DraftingException("模板固定发函年份为"+year.group(1)+"年，与材料发函日期冲突；原文不能自动修改，请使用年份为空白的模板或核对发函日期",422);
        }
    }
    private static void requireCompatibleDeadline(DraftingFacts.Factors factors,String template){
        String source=String.join("\n",factors.deadline(),factors.purpose(),factors.facts());
        var declared=Pattern.compile("催告(?:履行)?期限[^。\\n]{0,160}?(?<![0-9年月/一二三四五六七八九十-])"+DAY_COUNT+"(?:个)?(自然日|工作日|日|天)").matcher(source);
        Set<String> limits=new HashSet<>();
        while(declared.find())limits.add(dayNumber(declared.group(1))+":"+dayUnit(declared.group(2)));
        if(limits.size()!=1)return; // Ambiguous materials must not pick an arbitrary period.
        var fixed=Pattern.compile("(?:收到|收悉|签收)本函[^。；\\n]{0,16}?(?<![0-9一二三四五六七八九十])"+DAY_COUNT+"(?:个)?(自然日|工作日|日|天)内").matcher(template);
        while(fixed.find())if(!limits.contains(dayNumber(fixed.group(1))+":"+dayUnit(fixed.group(2))))
            throw new DraftingException("模板固定的催告期限与本次材料明确约定的催告期限不一致；严格填充不能修改固定文字，请更换期限匹配或使用期限占位符的模板",422);
    }
    private static int dayNumber(String text){
        try{return Integer.parseInt(text);}catch(NumberFormatException ignored){
            int ten=text.indexOf('十');
            if(ten<0)return "一二三四五六七八九".indexOf(text)+1;
            int tens=ten==0?1:"一二三四五六七八九".indexOf(text.substring(0,ten))+1;
            int ones=ten==text.length()-1?0:"一二三四五六七八九".indexOf(text.substring(ten+1))+1;
            return tens*10+ones;
        }
    }
    private static String dayUnit(String text){return text.equals("工作日")?"工作日":"自然日";}
    public static String modelInput(String request){
        var document=expected(request);if(document==null)return request;requireCompatible(request,document.blocks());
        String calculationInput=request+"\n"+DraftingCalculations.PROMPT+"\n本次模板空白清单：\n"+DraftingCalculations.slots(document.blocks());
        if(document.blocks().stream().anyMatch(b->b.fields()!=null))calculationInput+="\n本模板使用独立字段映射。请仅返回{\"documentTemplate\":\"本次版本ID\",\"fieldValues\":[{\"fieldId\":\"字段id\",\"value\":\"不含固定单位的值\",\"quote\":\"合同原文逐字依据\"}],\"calculations\":[]}，不输出或改写整段blocks。按fields中的label从合同全文提取，字面下划线和单位由程序保留。只使用editable=true且enabled=true的字段ID，每个ID最多一次。无来源的字段省略整条记录，不返回空value、空quote、null或原占位符。number值只包含数字，不带年/月/日等固定单位。金额及推导日期仍按计算协议声明SOURCE等方案，slot使用空白清单序号。";
        var known=TemplateSourceValues.read(request);if(known.isEmpty())return calculationInput;
        List<TemplateLayout.Block> hints=new ArrayList<>();String previous="";
        for(var block:document.blocks()){
            if(block.editable()){String filled=TemplateSourceValues.fill(block,block.text(),previous,known);if(!filled.equals(block.text()))hints.add(new TemplateLayout.Block(block.id(),filled,true));}
            previous=block.text();
        }
        try{return calculationInput+"\n根据资料明确标注的值，以下空白已匹配，请完整保留这些填充值；其他空白继续根据全文填写，固定文字仍以原模板为准：\n"+JSON.writeValueAsString(hints);}
        catch(Exception e){throw new DraftingException("模板字段匹配失败",422);}
    }
    public static String finish(String raw,String request){
        return finish(raw,request,false);
    }
    public static String finish(String raw,String request,boolean saving){
        Document expected=expected(request);
        requireCompatible(request,expected.blocks());
        String clean=Objects.toString(raw,"").strip().replaceFirst("(?is)^```(?:json)?\\s*\\n([\\s\\S]*?)\\n?```$","$1");
        Document answer=fieldAnswer(clean,request,expected);
        if(answer==null||!expected.documentTemplate().equals(answer.documentTemplate())||answer.blocks()==null)throw new DraftingException("模型未按模板填写区域返回结果，请重新生成",422);
        Map<String,String> values=new HashMap<>();Set<String> allowed=new HashSet<>();expected.blocks().forEach(b->allowed.add(b.id()));
        for(var b:answer.blocks())if(b==null||b.id()==null||b.text()==null||!allowed.contains(b.id())||values.put(b.id(),b.text())!=null)throw new DraftingException("模型返回了重复或未知的模板区域",422);
        List<TemplateLayout.Block> result=new ArrayList<>();var known=saving?Map.<String,String>of():TemplateSourceValues.read(request);String previous="";
        for(var b:expected.blocks()){
            String value=b.editable()?values.get(b.id()):b.text();
            if(b.editable()&&value!=null){String cleaned=DocumentCitations.clean(value);if(!cleaned.equals(value.strip()))value=cleaned;}
            if(value==null||value.length()>50000||value.matches("(?s).*```.*"))throw new DraftingException("模板填写区域不完整，请重新生成或补充要素",422);
            if(b.editable()){
                value=TemplateFields.restoreBlanks(b,value);
                TemplateLayout.validateReplacement(b,value);
                value=TemplateSourceValues.fill(b,value,previous,known);
                TemplateLayout.validateReplacement(b,value);
            }
            result.add(new TemplateLayout.Block(b.id(),value,b.editable()));
            previous=b.text();
        }
        // Explicit form values were source-checked at composition. Combining them must not
        // infer missing totals/dates, replace manual values or run model calculation plans.
        var calculated=TemplateForm.applies(request)?new DraftingCalculations.Outcome(result,null,null):DraftingCalculations.apply(expected.blocks(),result,answer.calculations(),request,saving);
        var coverage=TemplateFillReport.assess(expected.blocks(),calculated.blocks(),calculated.results());
        var document=new Document(expected.documentTemplate(),TemplateFields.presentation(expected.blocks(),calculated.blocks()),calculated.plans(),calculated.results(),coverage);

        try{return JSON.writeValueAsString(document);}catch(Exception e){throw new DraftingException("模板成稿保存失败",500);}
    }
    private static Document fieldAnswer(String raw,String request,Document expected){
        try{
            var root=JSON.readTree(raw);if(!root.has("fieldValues"))return read(raw);
            if(!expected.documentTemplate().equals(root.path("documentTemplate").asText())||!root.path("fieldValues").isArray()||root.path("fieldValues").size()>500)throw new DraftingException("模型返回的字段版本或格式无效，请重试",422);
            Map<String,TemplateFields.Field> allowed=new HashMap<>();for(var b:expected.blocks())if(b.editable()&&b.fields()!=null)for(var f:b.fields())if(f.enabled())allowed.put(f.id(),f);
            Map<String,String> values=new HashMap<>();Set<String> seen=new HashSet<>();String source=DraftingCalculations.source(request).replaceAll("\\s","");
            for(var row:root.path("fieldValues")){
                String id=row.path("fieldId").asText();var field=allowed.get(id);
                if(field==null||!seen.add(id)||!row.path("value").isTextual()||!row.path("quote").isTextual())throw new DraftingException("模型返回了重复或未知的填写字段",422);
                String value=row.path("value").asText(),quote=row.path("quote").asText().replaceAll("\\s","");
                // An explicit abstention in BOTH columns supplies no fact. Normalize it
                // to an omitted field, retain the original blank and report it unresolved.
                // A nonempty claimed fact still requires the full source validation below.
                if(abstention(value)&&abstention(quote))continue;
                if(value.length()>20000||!TemplateSourceValues.meaningfulValue(value)||quote.isBlank()||!source.contains(quote)||!grounded(value,quote))throw new DraftingException("填写字段缺少合同原文依据，请补充资料或重新提取",422);
                if(!field.unit().isEmpty()&&value.endsWith(field.unit()))throw new DraftingException("填写值重复包含模板固定单位，请仅返回字段数值",422);
                var original=expected.blocks().stream().filter(block->block.id().equals(field.blockId())).findFirst().orElseThrow();
                values.put(id,TemplateFields.renderValue(field.label(),original.text().substring(field.end()),value));
            }
            List<TemplateLayout.Block> blocks=new ArrayList<>();
            for(var b:expected.blocks()){
                if(!b.editable()||b.fields()==null){blocks.add(b);continue;}
                StringBuilder text=new StringBuilder();int position=0;
                for(var f:b.fields())if(f.enabled()){text.append(b.text(),position,f.start()).append(values.getOrDefault(f.id(),b.text().substring(f.start(),f.end())));position=f.end();}
                text.append(b.text().substring(position));blocks.add(new TemplateLayout.Block(b.id(),text.toString(),true));
            }
            List<DraftingCalculations.Plan> plans=root.has("calculations")?JSON.convertValue(root.path("calculations"),new com.fasterxml.jackson.core.type.TypeReference<List<DraftingCalculations.Plan>>(){}):null;
            return new Document(expected.documentTemplate(),blocks,plans,null);
        }catch(DraftingException e){throw e;}catch(Exception e){throw new DraftingException("模型未按模板字段协议返回结果，请重新生成",422);}
    }
    private static boolean grounded(String value,String quote){
        String normalized=value.replaceAll("[\\s,，]","");String compact=quote.replaceAll("[,，]","");if(compact.contains(normalized)&&!normalized.isBlank())return true;
        try{var expected=new java.math.BigDecimal(normalized);var m=Pattern.compile("[0-9]+(?:\\.[0-9]+)?").matcher(compact);while(m.find())if(expected.compareTo(new java.math.BigDecimal(m.group()))==0)return true;}catch(NumberFormatException ignored){}
        return false;
    }
    private static boolean abstention(String value){
        String text=Objects.toString(value,"").strip();
        if(text.isEmpty()||text.matches("[_＿\\s　]+")||text.matches("\\{\\{[^{}]{1,80}}}"))return true;
        return text.length()<=100&&text.matches("(?:【[^】]*】|\\[[^]]*]|未提供|未明确|未确认|待补充|待确认|待核实|待填写)")&&!TemplateSourceValues.meaningfulValue(text);
    }
    public static String plain(Document d){return d.blocks().stream().map(TemplateLayout.Block::text).reduce((a,b)->a+"\n\n"+b).orElse("");}
    public static StreamingChatResponseHandler wrap(StreamingChatResponseHandler delegate,String request){
        return wrap(delegate,request,null);
    }
    public static StreamingChatResponseHandler wrap(StreamingChatResponseHandler delegate,String request,java.util.function.BiConsumer<String,StreamingChatResponseHandler> repair){
        return new StreamingChatResponseHandler(){
            private final StringBuilder raw=new StringBuilder();private final AtomicBoolean terminal=new AtomicBoolean();
            public void onPartialResponse(String text){if(!terminal.get())raw.append(text);}
            public void onCompleteResponse(ChatResponse response){
                if(!terminal.compareAndSet(false,true))return;
                try{
                    String value=raw.isEmpty()&&response!=null?response.aiMessage().text():raw.toString();
                    String checked=finish(value,request);var report=read(checked).fillReport();
                    // Retry a model omission once; real missing data remains an explicit partial draft.
                    // A configuration gap cannot be repaired by granting the model new write positions.
                    if(repair!=null&&report!=null&&"partial".equals(report.status())&&report.issues().stream().anyMatch(issue->"unfilled".equals(issue.reason()))){
                        String missing=report.issues().stream().filter(issue->"unfilled".equals(issue.reason())).map(issue->issue.fieldId()+"（"+issue.label()+"）").collect(java.util.stream.Collectors.joining("、"));
                        try{repair.accept("以下模板字段仍未填写："+missing+"。请重新核对本次原文，已提供依据的字段必须返回fieldValues和逐字quote。未提供或含糊的信息继续省略，不得猜测，也不得把贷款总额当作当前欠款。",wrap(delegate,request));}
                        catch(Exception failure){delegate.onError(failure);}
                        return;
                    }
                    delegate.onPartialResponse(checked);delegate.onCompleteResponse(response);
                }catch(DraftingException e){if(repair==null)delegate.onError(e);else try{repair.accept(e.getMessage(),wrap(delegate,request));}catch(Exception failure){delegate.onError(failure);}}
            }
            public void onError(Throwable error){if(terminal.compareAndSet(false,true))delegate.onError(error);}
        };
    }
}
