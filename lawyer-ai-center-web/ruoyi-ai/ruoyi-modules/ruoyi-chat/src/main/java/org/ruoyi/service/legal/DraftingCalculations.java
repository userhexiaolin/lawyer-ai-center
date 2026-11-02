package org.ruoyi.service.legal;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

/** Source-bound, decimal-only calculations. Model-provided answers are never evaluated or trusted. */
public final class DraftingCalculations {
    private DraftingCalculations() {}
    private static final BigDecimal LIMIT = new BigDecimal("1000000000000000");
    private static final Pattern NUMBER = Pattern.compile("(?<![\\d.,])([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?)\\s*(万元|元|%|％|天|日|期|个月|个|倍)?");
    private static final Pattern FOREIGN_CURRENCY = Pattern.compile("美元|港元|欧元|日元|新加坡元|澳元|外币|USD|HKD|EUR|\\$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE = Pattern.compile("(\\d{4})[年/-]\\s*(\\d{1,2})[月/-]\\s*(\\d{1,2})日?");
    private static final Pattern NEGATED = Pattern.compile("未提供|未确定|未确认|未明确|不采用|不得采用|尚未约定|待确认|待核实|假设|示例");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("uuuu-M-d").withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern PROTECTED = Pattern.compile("本金|金额|利息|罚息|违约金|合计|总额|余额|天数|日数|到期日|截止日|届满日|计算");

    public static final String PROMPT = """
        模板中需要推算的金额或日期不得由模型心算。JSON可增加calculations数组，逐项声明计算方案，结果由程序写入空白；blocks中该空白保留原样。
        每项格式：{"id":"c1","blockId":"p0","slot":0,"label":"本息合计","operation":"SUM","inputs":[{"type":"money","value":"10000","quote":"到期本金10000元"},{"type":"money","value":"500","quote":"未付正常利息500元"}]}。
        slot是原段落所有占位符/连续空格/下划线空白从0开始的序号，严格使用本次提供的空白清单；一个空白最多一个方案。输入value为十进制字符串，money单位元，rate为百分数（年利率6%填6），date为yyyy-MM-dd，number为数值；quote必须逐字摘录本次起草要素，包含该数值及业务含义，不能引用模板示例。引用前项结果用{"ref":"c1"}，只能引用已完成项。
        支持SOURCE（1个money/date/number直接引用原文）、SUM（2至20个money相加）、DIFFERENCE（两个money相减）、PRODUCT/DIVIDE（money与number乘除）、SIMPLE_INTEREST（money本金、rate百分数、date起日、date止日）、DATE_DAYS（两个date）、DATE_ADD/DATE_SUBTRACT（date与number整天）。金额/日期字段已有明确结果而未在预填清单中时也须声明SOURCE并附无歧义原文。仅处理人民币元；复利、分段利率、还款摊销、月末顺延等复杂算法不得简化为单利。
        运算选择须忠于明确的实际履行及诉求；不得仅依据贷款总额/合同期限认定当前欠款或已违约。金额输出保留两位小数四舍五入，中间计算不提前舍入；相加引用的是各分项的两位小数金额。
        DATE_DAYS及SIMPLE_INTEREST必须有convention和conventionQuote（逐字原文）。convention可为EXCLUDE_START_INCLUDE_END（不计首日计末日）、INCLUDE_START_EXCLUDE_END（计首日不计末日）、INCLUDE_BOTH（首尾均计）、EXCLUDE_BOTH（首尾均不计）。不明确则不猜，仍返回该方案并保留原空白，程序显示缺项。
        SIMPLE_INTEREST只接受明确的单利：再提供ruleQuote（明确单利的原文）、ratePeriod（year/month/day）、basis（年360或365、月30，日1）及basisQuote（本次明确的计息基数原文，日利率可省）。rate输入摘录必须包含年利率/月利率/日利率相应字样；不得默认年365或月30。
        资料缺失、不支持或有冲突时保留空白。不得自行输出calculationResults、已复核状态、任意程序、脚本或表达式。
        """;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Operand(String type, String value, String quote, String ref) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Plan(String id, String blockId, int slot, String label, String operation, List<Operand> inputs,
                       String convention, String conventionQuote, String ratePeriod, Integer basis, String basisQuote, String ruleQuote) {}
    public record Result(String id, String blockId, int slot, String label, String status, String value,
                         String unit, String formula, List<String> sources, String message) {}
    public record Outcome(List<TemplateLayout.Block> blocks, List<Plan> plans, List<Result> results) {}
    private record Value(String type, String text, List<String> sources) {
        BigDecimal decimal() { return new BigDecimal(text); }
        LocalDate date() { return parseDate(text); }
    }

    static String source(String request) {
        var factors = DraftingFacts.input(request);
        if (factors != null) return String.join("\n", factors.parties(), factors.facts(), factors.purpose(), factors.deadline());
        var litigation = LitigationDrafting.input(request);
        // Litigation JSON uses a different schema; do not use template text as evidence.
        return litigation == null ? "" : String.join("\n", litigation.plaintiff(), litigation.defendant(), litigation.claims(), litigation.facts(), litigation.evidence(), litigation.court());
    }

    public static String slots(List<TemplateLayout.Block> blocks) {
        StringBuilder result = new StringBuilder();
        for (var block : blocks) if (block.editable()) {
            var ranges = TemplateFields.ranges(block);
            for (int i = 0; i < ranges.size(); i++) {
                var range = ranges.get(i);
                result.append(block.id()).append(" slot=").append(i).append("：")
                    .append(block.text(), Math.max(0, range[0] - 16), Math.min(block.text().length(), range[1] + 8)).append('\n');
            }
        }
        return result.toString();
    }

    public static Outcome apply(List<TemplateLayout.Block> original, List<TemplateLayout.Block> filled,
                                List<Plan> proposed, String request, boolean saving) {
        List<Plan> plans = new ArrayList<>(proposed == null ? List.of() : proposed);
        autoTotals(original, request, plans);
        autoLoanFields(original, request, plans);
        if (plans.size() > 50) throw invalid("计算项不能超过50项");
        Map<String, TemplateLayout.Block> originals = new LinkedHashMap<>();
        original.forEach(block -> originals.put(block.id(), block));
        Map<String, List<int[]>> slotRanges = new HashMap<>();
        Map<String, List<String>> sourceValues = new HashMap<>();
        var known = TemplateSourceValues.read(request);
        for (var block : original) {
            slotRanges.put(block.id(), TemplateFields.ranges(block));
            sourceValues.put(block.id(), TemplateFields.values(block, TemplateSourceValues.fill(block, block.text(), "", known)));
        }
        Map<String, List<String>> values = new HashMap<>();
        filled.forEach(block -> values.put(block.id(), new ArrayList<>(TemplateFields.values(originals.get(block.id()), block.text()))));
        Map<String, Value> completed = new HashMap<>();
        Set<String> targets = new HashSet<>(), ids = new HashSet<>();
        List<Result> results = new ArrayList<>();
        String source = source(request);
        for (var plan : plans) {
            if (plan == null || plan.id() == null || !plan.id().matches("[A-Za-z][A-Za-z0-9_-]{0,39}") || !ids.add(plan.id())) throw invalid("计算项编号缺失或重复");
            var block = originals.get(plan.blockId());
            if (block == null || !block.editable() || plan.slot() < 0 || plan.slot() >= slotRanges.get(block.id()).size()) throw invalid("计算目标不是模板允许填写的空白");
            if (plan.label() == null || plan.label().isBlank() || plan.label().length() > 80) throw invalid("计算字段名称无效");
            String target = plan.blockId() + ":" + plan.slot();
            if (!targets.add(target)) throw invalid("同一模板空白不能重复计算");
            String replacement;
            try {
                var value = calculate(plan, source, completed);
                replacement = value.text();
                String explicitValue = sourceValues.get(block.id()).get(plan.slot());
                if (!explicitValue.equals(originalSlot(block, plan.slot())) && !sameValue(explicitValue, replacement))
                    throw new IllegalArgumentException("程序结果与材料明确记载的该字段不一致，请核对明细、截止日及计算口径后重新生成");
                completed.put(plan.id(), value);
                results.add(new Result(plan.id(), plan.blockId(), plan.slot(), plan.label(), "calculated", replacement,
                    value.type().equals("money") ? "元" : value.type().equals("date") ? "日期" : plan.operation().equals("DATE_DAYS") ? "天" : "", formula(plan, completed), value.sources(), "程序计算完成，输入及适用口径待人工复核"));
            } catch (IllegalArgumentException | ArithmeticException e) {
                replacement = originalSlot(block, plan.slot());
                results.add(new Result(plan.id(), plan.blockId(), plan.slot(), plan.label(), "pending", null, "", "", List.of(), e instanceof ArithmeticException ? "除数为0或天数不是有效整数，请核对输入后重新生成" : e.getMessage()));
            }
            var targetRange = slotRanges.get(block.id()).get(plan.slot());
            String following = block.text().substring(targetRange[1]);
            if (!replacement.equals(originalSlot(block, plan.slot())) && DATE.matcher(replacement).matches() && !following.isEmpty() && "年月日".indexOf(following.charAt(0)) >= 0) {
                var date = parseDate(replacement);
                replacement = String.valueOf(following.charAt(0) == '年' ? date.getYear() : following.charAt(0) == '月' ? date.getMonthValue() : date.getDayOfMonth());
            }
            replace(values, plan.blockId(), plan.slot(), replacement, saving);
        }
        // Known derived fields cannot silently fall back to model arithmetic when a plan was omitted.
        if (!source.isBlank()) for (var block : original) if (block.editable()) {
            var ranges = slotRanges.get(block.id());
            for (int i = 0; i < ranges.size(); i++) {
                if (targets.contains(block.id() + ":" + i)) continue;
                var range = ranges.get(i);
                String slot = block.text().substring(range[0], range[1]), context = slot.startsWith("{{") ? slot : block.text().substring(Math.max(0, range[0] - 10), range[0]);
                if (!PROTECTED.matcher(context).find()) continue;
                if (!slot.startsWith("{{") && !context.matches("(?s).*(?:本金|金额|利息|罚息|违约金|合计|总额|余额|天数|日数|到期日|截止日|届满日)[：: \\t]*")) continue;
                String current = values.get(block.id()).get(i);
                String sourceValue = sourceValues.get(block.id()).get(i);
                if (!sourceValue.equals(slot) && sameValue(sourceValue, current)) continue;
                replace(values, block.id(), i, slot, saving);
                results.add(new Result("missing-" + block.id() + "-" + i, block.id(), i, context.replaceAll("[{}]", "").strip(), "pending", null, "", "", List.of(), "缺少有来源的计算方案；请补充计算依据后重新生成"));
            }
        }
        List<TemplateLayout.Block> blocks = new ArrayList<>();
        for (var block : original) {
            var ranges = slotRanges.get(block.id());
            StringBuilder text = new StringBuilder(); int position = 0;
            for (int i = 0; i < ranges.size(); i++) {
                var range = ranges.get(i);
                text.append(block.text(), position, range[0]).append(values.get(block.id()).get(i)); position = range[1];
            }
            text.append(block.text().substring(position));
            blocks.add(new TemplateLayout.Block(block.id(), text.toString(), block.editable()));
        }
        return new Outcome(blocks, plans.isEmpty() ? null : plans, results.isEmpty() ? null : results);
    }

    private static boolean sameValue(String a, String b) {
        if (a.equals(b)) return true;
        if (DATE.matcher(a).matches() && DATE.matcher(b).matches()) try { return parseDate(a).equals(parseDate(b)); } catch (IllegalArgumentException ignored) { return false; }
        try { return new BigDecimal(a).compareTo(new BigDecimal(b)) == 0; } catch (NumberFormatException ignored) { return false; }
    }
    private static void autoTotals(List<TemplateLayout.Block> blocks, String request, List<Plan> plans) {
        var known = TemplateSourceValues.read(request);
        if (!known.containsKey("principal") || !known.containsKey("interest")) return;
        String source = source(request);
        String principal = labelledQuote(source, "(?:已到期(?:逾期)?本金|逾期本金)(?:合计)?",known.get("principal")), interest = labelledQuote(source, "(?:已到期正常利息|未付正常利息|逾期正常利息)(?:合计)?",known.get("interest"));
        if (principal == null || interest == null) return;
        for (var block : blocks) if (block.editable()) {
            var ranges = TemplateFields.ranges(block);
            for (int i = 0; i < ranges.size(); i++) {
                int slotIndex = i; var range = ranges.get(i);
                String slot = block.text().substring(range[0], range[1]), context = slot.startsWith("{{") ? slot : block.text().substring(Math.max(0, range[0] - 8), range[0]);
                boolean total = slot.startsWith("{{") ? slot.matches("\\{\\{(?:本息合计|到期本息)}}") : context.matches("(?s).*(?:本息合计|到期本息)[：: \\t]*");
                if (!total) continue;
                var targets = plans.stream().filter(p -> p != null && block.id().equals(p.blockId()) && p.slot() == slotIndex).toList();
                // A model cannot bypass an unambiguous sum with SOURCE or another formula.
                // Retain its ID so later references use the authoritative program result.
                if (targets.size() > 1) continue; // Normal validation must reject duplicate targets.
                var automatic = new Plan(targets.isEmpty() ? "auto-" + block.id() + "-" + i : targets.get(0).id(), block.id(), i, "本息合计", "SUM", List.of(new Operand("money", known.get("principal"), principal, null), new Operand("money", known.get("interest"), interest, null)), null, null, null, null, null, null);
                if (targets.isEmpty()) plans.add(automatic); else plans.set(plans.indexOf(targets.get(0)), automatic);
            }
        }
    }
    private static String labelledQuote(String source, String label,String expected) {
        var matcher = Pattern.compile(label + "[\\s：:为]*[0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?\\s*元").matcher(source);
        while (matcher.find()) try {
            if(!numericValues(matcher.group(),"money").equals(Set.of(new BigDecimal(expected).stripTrailingZeros())))continue;
            return quote(matcher.group(), source);
        } catch (IllegalArgumentException ignored) { /* Try another explicit occurrence. */ }
        return null;
    }

    /** Explicit labelled source amounts/dates remain usable when a model omits plans. */
    private static void autoLoanFields(List<TemplateLayout.Block> blocks, String request, List<Plan> plans) {
        var known=TemplateSourceValues.read(request);String source=source(request);
        String penalty=known.containsKey("penalty")?labelledQuote(source,"(?:逾期罚息|罚息)(?:合计)?",known.get("penalty")):null;
        String principal=known.containsKey("principal")?labelledQuote(source,"(?:已到期(?:逾期)?本金|逾期本金)(?:合计)?",known.get("principal")):null;
        String interest=known.containsKey("interest")?labelledQuote(source,"(?:已到期正常利息|未付正常利息|逾期正常利息)(?:合计)?",known.get("interest")):null;
        String deadline=null;
        if(known.containsKey("demandDate")){
            var dates=Pattern.compile(TemplateSourceValues.DEADLINE_LABEL+"[\\s：:为]*(\\d{4}\\s*年\\s*\\d{1,2}\\s*月\\s*\\d{1,2}\\s*日)").matcher(source);
            while(dates.find())if(dates.group(1).replaceAll("\\s","").equals(known.get("demandDate"))){deadline=dates.group();break;}
        }
        for(var block:blocks)if(block.editable()){
            var ranges=TemplateFields.ranges(block);
            for(int i=0;i<ranges.size();i++){
                var range=ranges.get(i);String slot=block.text().substring(range[0],range[1]);
                String label=slot.startsWith("{{")?slot.substring(2,slot.length()-2).strip():"";
                int slotIndex=i;var targets=plans.stream().filter(p->p!=null&&block.id().equals(p.blockId())&&p.slot()==slotIndex).toList();
                if(targets.size()>1)continue; // Preserve duplicate-target rejection.
                List<Operand> inputs=null;String operation="SOURCE";
                if((label.equals("罚息")||label.equals("逾期罚息"))&&penalty!=null&&targets.isEmpty())
                    inputs=List.of(new Operand("money",known.get("penalty"),penalty,null));
                else if(label.equals("本次催收总额")&&principal!=null&&interest!=null&&penalty!=null){
                    operation="SUM";inputs=List.of(new Operand("money",known.get("principal"),principal,null),new Operand("money",known.get("interest"),interest,null),new Operand("money",known.get("penalty"),penalty,null));
                }
                else if((label.equals("催告届满日")||label.equals("催告截止日"))&&deadline!=null&&targets.isEmpty())
                    inputs=List.of(new Operand("date",known.get("demandDate"),deadline,null));
                if(inputs==null)continue;
                var automatic=new Plan(targets.isEmpty()?"auto-"+block.id()+"-"+i:targets.get(0).id(),block.id(),i,label,operation,inputs,null,null,null,null,null,null);
                if(targets.isEmpty())plans.add(automatic);else plans.set(plans.indexOf(targets.get(0)),automatic);
            }
        }
    }

    private static void replace(Map<String, List<String>> values, String block, int slot, String replacement, boolean saving) {
        if (saving && !values.get(block).get(slot).equals(replacement)) throw invalid("计算字段已修改或依据不足，保存/导出已停止；请核对起草要素并重新生成计算结果，当前编辑仍保留");
        values.get(block).set(slot, replacement);
    }
    private static String originalSlot(TemplateLayout.Block block, int slot) { var range = TemplateFields.ranges(block).get(slot); return block.text().substring(range[0], range[1]); }
    private static Value calculate(Plan plan, String source, Map<String, Value> completed) {
        if (plan.inputs() == null || plan.inputs().size() < ("SOURCE".equals(plan.operation()) ? 1 : 2) || plan.inputs().size() > 20) throw new IllegalArgumentException("计算输入项数量不正确，缺失时请补充材料");
        List<Value> inputs = plan.inputs().stream().map(input -> operand(input, source, completed)).toList();
        List<String> sources = new ArrayList<>(); inputs.forEach(value -> sources.addAll(value.sources()));
        String operation = Objects.toString(plan.operation(), "");
        String type = "money", output;
        if (operation.equals("SOURCE")) {
            count(inputs, 1); var value = inputs.get(0);
            if (value.type().equals("rate")) throw new IllegalArgumentException("利率不能作为金额或天数结果");
            type = value.type(); output = type.equals("money") ? money(value.decimal()) : type.equals("date") ? value.date().getYear() + "年" + value.date().getMonthValue() + "月" + value.date().getDayOfMonth() + "日" : value.text();
        } else if (operation.equals("SUM")) {
            BigDecimal sum = BigDecimal.ZERO;
            for (var value : inputs) { requireType(value, "money"); sum = sum.add(value.decimal()); }
            output = money(sum);
        } else if (Set.of("DIFFERENCE", "PRODUCT", "DIVIDE").contains(operation)) {
            count(inputs, 2); requireType(inputs.get(0), "money");
            requireType(inputs.get(1), operation.equals("DIFFERENCE") ? "money" : "number");
            var a = inputs.get(0).decimal(); var b = inputs.get(1).decimal();
            output = money(switch (operation) { case "DIFFERENCE" -> a.subtract(b); case "PRODUCT" -> a.multiply(b); default -> a.divide(b, 2, RoundingMode.HALF_UP); });
        } else if (operation.equals("DATE_DAYS")) {
            count(inputs, 2); requireType(inputs.get(0), "date"); requireType(inputs.get(1), "date");
            output = String.valueOf(days(inputs.get(0).date(), inputs.get(1).date(), plan, source, sources)); type = "number";
        } else if (operation.equals("DATE_ADD") || operation.equals("DATE_SUBTRACT")) {
            count(inputs, 2); requireType(inputs.get(0), "date"); requireType(inputs.get(1), "number");
            long days = inputs.get(1).decimal().longValueExact();
            if (days < 0 || days > 36500) throw new IllegalArgumentException("按天推算日期的天数须为0至36500的整数");
            var date = inputs.get(0).date().plusDays(operation.equals("DATE_ADD") ? days : -days);
            if (date.getYear() < 1900 || date.getYear() > 2200) throw new IllegalArgumentException("计算日期超出1900至2200年范围");
            output = date.getYear() + "年" + date.getMonthValue() + "月" + date.getDayOfMonth() + "日"; type = "date";
        } else if (operation.equals("SIMPLE_INTEREST")) {
            count(inputs, 4); requireType(inputs.get(0), "money"); requireType(inputs.get(1), "rate"); requireType(inputs.get(2), "date"); requireType(inputs.get(3), "date");
            String rule = quote(plan.ruleQuote(), source);
            if (!rule.contains("单利") || rule.matches("(?s).*(?:复利|分段|分期|利率调整).*")) throw new IllegalArgumentException("缺少明确的单利依据，复利/分段/摊销须另行核算");
            sources.add(rule);
            String period = Objects.toString(plan.ratePeriod(), "");
            String rateLabel = switch (period) { case "year" -> "年利率"; case "month" -> "月利率"; case "day" -> "日利率"; default -> throw new IllegalArgumentException("须明确年、月或日利率周期"); };
            if (!inputs.get(1).sources().get(0).contains(rateLabel)) throw new IllegalArgumentException("利率周期与原文不一致");
            int basis = plan.basis() == null ? 0 : plan.basis();
            if (period.equals("day")) { if (basis != 1) throw new IllegalArgumentException("日利率计算基数须为1"); }
            else {
                String basisQuote = quote(plan.basisQuote(), source);
                if (!(period.equals("year") && (basis == 360 || basis == 365) || period.equals("month") && basis == 30)
                    || !basisQuote.matches("(?s).*(?:每年|一年|年按|年计息|年基数|每月|一月|月按|月计息|月基数)[^。；;]{0,16}(?<!\\d)" + basis + "\\s*(?:天|日).*"))
                    throw new IllegalArgumentException("缺少明确的年/月计息基数，不能默认365、360或30天");
                sources.add(basisQuote);
            }
            long days = days(inputs.get(2).date(), inputs.get(3).date(), plan, source, sources);
            output = money(inputs.get(0).decimal().multiply(inputs.get(1).decimal()).multiply(BigDecimal.valueOf(days))
                .divide(BigDecimal.valueOf((long) basis * 100), 2, RoundingMode.HALF_UP));
        } else throw new IllegalArgumentException("不支持该算法；仅支持金额基础运算、明确单利及按天的日期计算");
        return new Value(type, output, sources.stream().distinct().toList());
    }

    private static Value operand(Operand input, String source, Map<String, Value> completed) {
        if (input == null) throw new IllegalArgumentException("计算输入缺失");
        if (input.ref() != null) {
            if (input.type() != null || input.value() != null || input.quote() != null) throw new IllegalArgumentException("引用结果不能同时声明自定义数值");
            var value = completed.get(input.ref());
            if (value == null) throw new IllegalArgumentException("依赖项未计算成功、引用顺序错误或存在循环");
            return value;
        }
        String quote = quote(input.quote(), source), type = Objects.toString(input.type(), "");
        if (type.equals("date")) {
            LocalDate date = parseDate(input.value());
            var candidates = dateValues(quote);
            if (candidates.size() != 1 || !candidates.contains(date)) throw new IllegalArgumentException("日期与所附原文不一致或存在多个日期");
            return new Value(type, date.toString(), List.of(quote));
        }
        if (!Set.of("money", "rate", "number").contains(type) || input.value() == null || !input.value().matches("[0-9]{1,16}(?:\\.[0-9]{1,10})?")) throw new IllegalArgumentException("计算数值或类型无效，禁止表达式和脚本");
        if (type.equals("money") && FOREIGN_CURRENCY.matcher(quote).find()) throw new IllegalArgumentException("当前仅支持人民币元，外币及汇率换算须另行核算");
        var value = new BigDecimal(input.value()).stripTrailingZeros();
        if (value.compareTo(LIMIT) > 0 || type.equals("rate") && value.compareTo(new BigDecimal("100")) > 0) throw new IllegalArgumentException("数值或利率超出支持范围");
        var candidates = numericValues(quote, type);
        if (candidates.size() != 1 || !candidates.contains(value)) throw new IllegalArgumentException("数值与原文不一致或摘录包含多个数值；请提供无歧义的字段摘录");
        return new Value(type, value.toPlainString(), List.of(quote));
    }

    private static String quote(String quote, String source) {
        if (quote == null || quote.length() < 2 || quote.length() > 1000 || !source.contains(quote) || NEGATED.matcher(quote).find()) throw new IllegalArgumentException("计算依据缺失、未确认或不属于本次起草要素，请核对并补充原文");
        int start = source.indexOf(quote);
        String prefix = source.substring(Math.max(0, start - 24), start).replaceFirst("(?s)^.*[。；;\\n]", "");
        if (NEGATED.matcher(prefix).find()) throw new IllegalArgumentException("该数值所在原文为否定、示例或待确认内容");
        return quote;
    }
    private static Set<BigDecimal> numericValues(String text, String type) {
        Set<BigDecimal> result = new HashSet<>(); var matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            String unit = Objects.toString(matcher.group(2), "");
            if (type.equals("money") && !Set.of("元", "万元").contains(unit) || type.equals("rate") && !Set.of("%", "％").contains(unit) || type.equals("number") && Set.of("元", "万元", "%", "％").contains(unit)) continue;
            BigDecimal value = new BigDecimal(matcher.group(1).replace(",", ""));
            if (unit.equals("万元")) value = value.multiply(BigDecimal.valueOf(10000));
            result.add(value.stripTrailingZeros());
        }
        return result;
    }
    private static Set<LocalDate> dateValues(String text) {
        Set<LocalDate> values = new HashSet<>(); var matcher = DATE.matcher(text);
        while (matcher.find()) try { values.add(parseDate(matcher.group())); } catch (IllegalArgumentException ignored) { /* Invalid dates cannot support a calculation. */ }
        return values;
    }
    private static LocalDate parseDate(String value) {
        if (value == null || !DATE.matcher(value).matches()) throw new IllegalArgumentException("日期须为完整且有效的年月日");
        var match = DATE.matcher(value); match.matches();
        try {
            var date = LocalDate.parse(match.group(1) + "-" + Integer.parseInt(match.group(2)) + "-" + Integer.parseInt(match.group(3)), DATE_FORMAT);
            if (date.getYear() < 1900 || date.getYear() > 2200) throw new IllegalArgumentException("日期超出支持范围");
            return date;
        } catch (java.time.DateTimeException e) { throw new IllegalArgumentException("日期无效，请核对闰年及月份天数"); }
    }
    private static long days(LocalDate start, LocalDate end, Plan plan, String source, List<String> sources) {
        if (end.isBefore(start)) throw new IllegalArgumentException("截止日期不能早于起算日期");
        String evidence = quote(plan.conventionQuote(), source);
        String expression = switch (Objects.toString(plan.convention(), "")) {
            case "EXCLUDE_START_INCLUDE_END" -> "不计首日计末日|不含首日含末日|不算头算尾|算尾不算头";
            case "INCLUDE_START_EXCLUDE_END" -> "(?<!不)计首日不计末日|(?<!不)含首日不含末日|算头不算尾";
            case "INCLUDE_BOTH" -> "首尾均计|含首尾两日|首尾两日均计入";
            case "EXCLUDE_BOTH" -> "首尾均不计|不含首尾两日|首尾两日均不计入";
            default -> throw new IllegalArgumentException("须明确是否计入首尾日，不能自行选定日期口径");
        };
        if (!Pattern.compile(expression).matcher(evidence).find()) throw new IllegalArgumentException("首尾日计算口径与原文不一致或不明确");
        sources.add(evidence);
        long days = ChronoUnit.DAYS.between(start, end);
        if (plan.convention().equals("INCLUDE_BOTH")) days++;
        if (plan.convention().equals("EXCLUDE_BOTH")) days = Math.max(0, days - 1);
        return days;
    }
    private static String money(BigDecimal amount) {
        if (amount.signum() < 0 || amount.compareTo(LIMIT) > 0) throw new IllegalArgumentException("结果为负数或超出支持范围，请核对金额及运算方向");
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
    private static void requireType(Value value, String type) { if (!value.type().equals(type)) throw new IllegalArgumentException("运算输入的单位或类型不匹配"); }
    private static void count(List<Value> inputs, int count) { if (inputs.size() != count) throw new IllegalArgumentException("该运算的输入项数量不正确"); }
    private static String formula(Plan plan, Map<String, Value> completed) {
        List<String> values = plan.inputs().stream().map(input -> input.ref() == null ? input.value() : completed.get(input.ref()).text()).toList();
        return switch (plan.operation()) {
            case "SOURCE" -> "直接引用起草要素中的明确数值：" + values.get(0);
            case "SUM" -> String.join(" + ", values);
            case "DIFFERENCE" -> String.join(" − ", values);
            case "PRODUCT" -> String.join(" × ", values);
            case "DIVIDE" -> String.join(" ÷ ", values);
            case "DATE_DAYS" -> values.get(0) + " 至 " + values.get(1) + "；" + plan.conventionQuote();
            case "SIMPLE_INTEREST" -> values.get(0) + " × " + values.get(1) + "% × " + formulaDays(values.get(2), values.get(3), plan.convention()) + "天 ÷ " + plan.basis() + "；" + plan.conventionQuote() + "；金额四舍五入至分";
            default -> values.get(0) + (plan.operation().equals("DATE_ADD") ? " + " : " − ") + values.get(1) + "天";
        };
    }
    private static long formulaDays(String start, String end, String convention) {
        long days = ChronoUnit.DAYS.between(parseDate(start), parseDate(end));
        return convention.equals("INCLUDE_BOTH") ? days + 1 : convention.equals("EXCLUDE_BOTH") ? Math.max(0, days - 1) : days;
    }
    private static DraftingException invalid(String message) { return new DraftingException(message, 422); }
}
