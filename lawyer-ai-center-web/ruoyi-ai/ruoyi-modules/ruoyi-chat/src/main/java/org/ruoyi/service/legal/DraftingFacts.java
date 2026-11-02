package org.ruoyi.service.legal;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;

/** Checks current drafting factors, independently of the template's example business facts. */
public final class DraftingFacts {
    private DraftingFacts(){}
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final String MARKER="---起草要素---";
    private static final Pattern MONEY=Pattern.compile("(?<![\\d.])(\\d+(?:,\\d{3})*(?:\\.\\d+)?)\\s*(万元|元)");
    private static final Pattern DATE=Pattern.compile("(\\d{4})\\s*年\\s*(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日");
    private static final Pattern COSTS=Pattern.compile("诉讼费(?:用)?|案件受理费|保全费|保险费|律师(?:代理)?费|罚息|复利|一切经济损失|全部经济损失|限制高消费|失信被执行人|罚款|拘留");
    public static final String PROMPT="""
        本次要将原模板适配为本案文书，不是只替换几个姓名或合同名称。
        editable=true的整段允许完全改写。先根据本次起草要素组织事实和请求，再写入对应段落；字体和排版由程序保留。
        模板中的贷款、按揭、催收、金额、违约认定、履行期限等均是旧业务示例，不是本案事实；与本案合同不符的内容必须完整重写。
        合同已提供的双方姓名、标的地址、签约日期、租期、租金、已收款和押金应准确写入事实段，金额保留阿拉伯数字及单位，日期写完整年月日，不能只改合同标题。
        合同约定、签约时收款与当前欠款是不同事实。未提供实际逾期、欠款和催告经过时，不写“多次索要”“已构成违约”等既成事实，不把月租或押金当欠款，不沿用模板默认的三日期限或费用主张。
        未提出具体费用请求时，删除模板中“全部损失、律师费等概由您承担”和失信、罚款、拘留等惩戒套话，不能将其作为本案既定后果；可在核实诉求后依法主张权利。
        合同未记载的编号写“合同未记载编号”；确需但未提供的律师姓名、电话、发函日期、发函方、收件人及具体诉求用【具体字段待补充/待确认】明确标记，不留空格、下划线、“年 月 日”或“本金 元”等空白。
        甲方乙方身份不等于发函方与收件人身份。没有明确指定时不得擅自确认委托关系；但必须在事实段明确写出合同甲乙双方的姓名或全称，不能用“甲方”“乙方”代替已知姓名。在抬头和委托说明处标待确认，不能直接把乙方公司当收件人，也不要使用“贵司”暗中指定方向。合同签约日期不能充当发函日期。
        贷款附件明确列出到期欠款时，正文必须分别写明“逾期本金”“正常利息”“本息合计”（不含罚息）、“逾期罚息”和“本次催收总额”（含罚息），不得省略明示的本息合计。保留核算截止日、逾期起算日、发函日和催告届满日。
        全部剩余本金可能含已到期本金，不能把全部剩余本金称为未到期本金。仅有专项委托时不声称常年法律顾问关系；本次只催收已到期款项时不追加提前到期请求、费用金额或惩戒后果。
        """;
    public record Factors(String parties,String facts,String purpose,String deadline){}
    public static boolean unconfirmedLetterParties(Factors factors){return factors!=null&&factors.purpose().contains("及发函方、收件人由委托人核实补充")&&!(factors.parties()+factors.purpose()).matches("(?s).*(?:发函方|收件人)[：:].*");}
    public static Factors input(String request){
        int start=Objects.toString(request,"").indexOf(MARKER);if(start<0)return null;
        try{
            var value=JSON.readTree(request.substring(start+MARKER.length()));
            for(String key:List.of("parties","facts","purpose","deadline"))if(!value.path(key).isTextual())return null;
            return new Factors(value.path("parties").asText(),value.path("facts").asText(),value.path("purpose").asText(),value.path("deadline").asText());
        }catch(Exception ignored){return null;}
    }
    private static Set<String> money(String value){
        Set<String> result=new LinkedHashSet<>();var matcher=MONEY.matcher(value);
        while(matcher.find()){var amount=new BigDecimal(matcher.group(1).replace(",",""));if(matcher.group(2).equals("万元"))amount=amount.multiply(BigDecimal.valueOf(10000));result.add(amount.stripTrailingZeros().toPlainString());}
        return result;
    }
    private static Set<String> dates(String value){
        Set<String> result=new LinkedHashSet<>();var matcher=DATE.matcher(value);
        while(matcher.find())result.add(Integer.parseInt(matcher.group(1))+"-"+Integer.parseInt(matcher.group(2))+"-"+Integer.parseInt(matcher.group(3)));
        return result;
    }
    private static boolean unsupportedEvent(String text){
        for(String sentence:text.split("[。；;\\n]")){
            if(sentence.matches("(?s).*(?:未提供|未确认|未核实|尚未|不能|不得|无法|不宜|如发生|若发生|是否|待确认|待核实).*"))continue;
            if(sentence.matches("(?s).*(?:多次(?:催告|催讨|索要)|(?:已|已经)(?:构成违约|逾期|拖欠)|无理由拒付).*"))return true;
        }
        return false;
    }
    public static void requireGrounded(String text,String request){
        requireGrounded(text,request,false);
    }
    public static void requireGrounded(String text,String request,boolean allowBlanks){
        var factors=input(request);if(factors==null)return;
        List<String> problems=new ArrayList<>();
        String current=factors.parties()+"\n"+factors.facts()+"\n"+factors.purpose()+"\n"+factors.deadline();
        if(current.contains("租赁")&&!current.matches("(?s).*(?:贷款|按揭).*" )&&text.matches("(?s).*(?:贷款|按揭|拖欠我行).*"))
            problems.add("仍含贷款或按揭模板示例，与本次租赁合同不符，须完整重写业务段落");
        if(!allowBlanks&&(text.matches("(?s).*年[\\s_＿　]*月[\\s_＿　]*日.*")||Pattern.compile("(?m)^\\s*(?:电话|联系电话|(?:承办|经办)?律师(?:姓名)?)\\s*[：:]?\\s*$").matcher(text).find()
            ||text.matches("(?s).*(?:本金|本息合计|利息|合同编号为)[\\s_＿　]+(?:元|[。；;]).*")))
            problems.add("仍有空白日期、金额或署名联系方式；已提供的信息须填写，未提供的信息须标明具体待补充字段");
        requireLoanAccounting(text,request,factors,problems,allowBlanks);
        // Automatic contract import promises a complete summary of the supplied main terms.
        // A user-written, narrower drafting purpose is not forced to repeat every contract term.
        if(!factors.purpose().startsWith("依据所导入合同的权利义务约定起草")){requireNoProblems(problems);return;}
        if(unconfirmedLetterParties(factors)){
            var recipient=Pattern.compile("(?m)^\\s*(?:致|收件人)[：:]([^\\n]*)").matcher(text);
            var client=Pattern.compile("(?:接受|受)([^。\\n]{1,120}?)的?委托").matcher(text);boolean assumedClient=false;
            while(client.find())if(!client.group(1).matches(".*(?:待确认|待补充).*")){
                if(allowBlanks&&blankField(client.group(1)))continue;
                assumedClient=true;break;
            }
            if((recipient.find()&&!recipient.group(1).matches(".*(?:待确认|待补充).*")&&!(allowBlanks&&blankField(recipient.group(1))))||text.contains("贵司")||assumedClient)
                problems.add("尚未明确发函方及收件人：抬头写【收件人待确认】，委托说明写接受【发函方待确认】的委托；不得擅自认定合同任一方就是委托人，也不得把乙方公司当作收件人");
        }
        var parties=Pattern.compile("(?m)(?:^|\\n)(?:甲方|乙方)[：:]\\s*([^\\n，,；;（(]+)").matcher(factors.parties());
        while(!allowBlanks&&parties.find()){
            String name=parties.group(1).strip().split("\\s+")[0];
            if(name.length()>1&&name.length()<=100&&!name.contains("待")&&!text.replaceAll("\\s","").contains(name))
                problems.add("遗漏已提供的当事人姓名或全称："+name+"；即使发函方尚待确认，事实段也须完整列出合同双方");
        }
        var missingMoney=money(factors.facts());missingMoney.removeAll(money(text));
        if(!allowBlanks&&!missingMoney.isEmpty())problems.add("遗漏已提供的金额（元）："+String.join("、",missingMoney)+"；须写明原本用途，不能当作当前欠款");
        var missingDates=dates(factors.facts()+"\n"+factors.deadline());missingDates.removeAll(dates(text));
        if(!allowBlanks&&!missingDates.isEmpty())problems.add("遗漏已提供的签约日期或合同期限："+String.join("、",missingDates));
        if(!unsupportedEvent(factors.facts())&&unsupportedEvent(text))problems.add("未提供实际违约或催告经过，不能沿用模板中已违约、多次索要等事实");
        if(!COSTS.matcher(factors.facts()).find())for(String sentence:text.split("[。；;\\n]")){
            if(COSTS.matcher(sentence).find()&&!sentence.matches("(?s).*(?:未提供|未确认|不能|不得|无法|不宜|待确认|待核实|尚待|未主张|未提出|未约定|裁判为准).*")){
                problems.add("本次未提出诉讼费用、律师费等费用请求或惩戒后果，应删除模板中全部损失由收件人承担、失信罚款等套话");break;
            }
        }
        requireNoProblems(problems);
    }
    private static void requireNoProblems(List<String> problems){if(!problems.isEmpty())throw new DraftingException("起草结果未通过合同事实校验："+String.join("；",problems),422);}
    private static boolean blankField(String text){return TemplateLayout.presentBlanks(text).replaceAll("[_＿\\s　]","").isBlank();}
    private static void requireLoanAccounting(String text,String request,Factors factors,List<String> problems,boolean allowBlanks){
        var known=TemplateSourceValues.read(request);
        if(!known.containsKey("principal")||!known.containsKey("interest"))return;
        String plain=text.replace("**","").replaceAll("[\\s,，]","");
        Map<String,String> labels=new LinkedHashMap<>();
        labels.put("principal","(?:已到期(?:逾期)?本金|到期未还本金|逾期本金)");labels.put("interest","(?:到期未付正常利息|未付正常利息|正常利息)");
        labels.put("total","(?:本息合计|本息小计)");labels.put("penalty","(?:逾期罚息|罚息合计|罚息)");labels.put("claimTotal","(?:本次催收总额|催收总额|欠款总额)");
        for(var entry:labels.entrySet())if(known.containsKey(entry.getKey())){
            var matcher=Pattern.compile(entry.getValue()+"[^0-9]{0,20}([0-9]+(?:\\.[0-9]+)?)元").matcher(plain);boolean present=false,written=false;
            while(matcher.find()){written=true;if(new BigDecimal(matcher.group(1)).compareTo(new BigDecimal(known.get(entry.getKey())))==0){present=true;break;}}
            if(!present&&(!allowBlanks||written))problems.add("遗漏或错误的到期欠款分项："+entry.getKey()+"，应按附件明确数值及用途列示，不能合并省略");
        }
        Set<String> writtenDates=new HashSet<>();
        var writtenDate=Pattern.compile("(\\d{4})[年/-](\\d{1,2})[月/-](\\d{1,2})日?").matcher(plain);
        while(writtenDate.find())writtenDates.add(Integer.parseInt(writtenDate.group(1))+"-"+Integer.parseInt(writtenDate.group(2))+"-"+Integer.parseInt(writtenDate.group(3)));
        for(String key:List.of("cutoffDate","overdueDate","letterDate","demandDate"))if(known.containsKey(key)){
            var expectedDate=Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日").matcher(known.get(key));
            if(!allowBlanks&&expectedDate.matches()&&!writtenDates.contains(Integer.parseInt(expectedDate.group(1))+"-"+Integer.parseInt(expectedDate.group(2))+"-"+Integer.parseInt(expectedDate.group(3))))
                problems.add("遗漏已明确的关键日期："+key+"（"+known.get(key)+"），须按原文写明，不能仅写相对期限");
        }
        String current=factors.facts()+"\n"+factors.purpose();
        if(text.contains("法律顾问")&&!current.contains("法律顾问"))problems.add("材料仅记载本次委托，不得虚构常年或专项法律顾问关系");
        if(current.contains("无提前到期声明")||factors.purpose().contains("仅主张已到期")||factors.purpose().contains("仅催收核算截止日已到期"))
            for(String sentence:text.split("[。；;\\n]"))if(sentence.matches("(?s).*(?:请求|要求)[^。；;\\n]{0,50}(?:全部剩余(?:未还)?本金|未到期本金).*" )&&!sentence.matches("(?s).*(?:不主张|不要求|不请求|不得|不能|不在本次).*"))
                problems.add("本次仅催收已到期款项，不能追加要求清偿全部剩余本金或未到期本金的请求");
        var mainContracts=Pattern.compile("主合同《([^》]+)》编号([A-Za-z0-9-]+)").matcher(factors.facts().replaceAll("\\s",""));
        while(!allowBlanks&&mainContracts.find())if(!plain.contains(mainContracts.group(2)))problems.add("遗漏主借款合同编号："+mainContracts.group(2)+"；须区分主借款合同与抵押担保合同");
        if(plain.matches("(?s).*抵押合同》[^。]{0,60}(?:以下简称|下称)[^。]{0,8}主合同.*"))problems.add("抵押担保合同不能被称为主借款合同，请按原件区分合同及签约主体");
        if(known.containsKey("remainingPrincipal")&&new BigDecimal(known.get("principal")).signum()>0){
            var remaining=Pattern.compile("(?:剩余)?未到期本金[^0-9]{0,15}([0-9]+(?:\\.[0-9]+)?)元").matcher(plain);
            while(remaining.find())if(new BigDecimal(remaining.group(1)).compareTo(new BigDecimal(known.get("remainingPrincipal")))==0)
                problems.add("全部剩余本金包含已到期本金，不能将其全部称为未到期本金；请按材料区分或不另列未到期数额");
            var excluded=Pattern.compile("(?<!未到期)剩余本金[^0-9]{0,15}([0-9]+(?:\\.[0-9]+)?)元([^。；;\\n]{0,50})不在本次催收范围").matcher(plain);
            while(excluded.find())if(new BigDecimal(excluded.group(1)).compareTo(new BigDecimal(known.get("remainingPrincipal")))==0&&!excluded.group(2).matches("(?s).*未到期本金[^0-9]{0,10}[0-9].*"))
                problems.add("本次催收包含已到期本金，不能把全部剩余本金整体排除在本次催收范围外；仅排除未到期部分");
        }
    }
}
