package org.ruoyi.service.legal;

import java.util.*;
import java.util.regex.Pattern;

/** Business compatibility only; shared people, property and money are not evidence of a shared debt. */
final class TemplateBusinessCompatibility {
    private TemplateBusinessCompatibility() {}

    private enum Business {
        LOAN("贷款/借款", "贷款合同|借款合同|借贷合同|按揭贷款|住房贷款|个人贷款|贷款本金|逾期本金|拖欠我行",
            "贷款合同|借款合同|借贷合同|按揭贷款|住房贷款|个人贷款|逾期本金"),
        PROPERTY("物业服务", "物业(?:服务|管理)费|物业服务合同|物业管理合同",
            "物业(?:服务|管理)(?:委托)?(?:合同|协议)|(?:欠缴|欠付|拖欠|未缴|未付|应缴|应付)[^。；，,\\n]{0,8}物业(?:服务|管理)?费|物业(?:服务|管理)?费[^。；，,\\n]{0,12}(?:欠缴|欠付|拖欠|未缴|未付|每月|每平方米|标准|合计|[0-9])"),
        LEASE("租赁", "租赁合同|租赁协议|租金|退租|腾退",
            "租赁合同|租赁协议|(?:欠付|拖欠|未付|应付)[^。；，,\\n]{0,8}租金|月租(?:金)?[：:为0-9]"),
        SALES("买卖", "买卖合同|购销合同|货款",
            "买卖合同|购销合同|(?:欠付|拖欠|未付|应付)[^。；，,\\n]{0,8}货款"),
        LABOR("劳动用工", "劳动合同|拖欠工资|支付工资|欠付工资",
            "劳动合同|(?:拖欠|欠付|未付)工资"),
        CONSTRUCTION("建设工程", "施工合同|工程款|工程价款",
            "施工合同|建设工程合同|(?:欠付|拖欠|未付|应付)[^。；，,\\n]{0,8}工程款");

        final String label;
        final Pattern template;
        final Pattern source;
        Business(String label, String template, String source) {
            this.label = label;
            this.template = Pattern.compile(template);
            this.source = Pattern.compile(source);
        }
    }

    static void require(DraftingFacts.Factors factors, List<TemplateLayout.Block> blocks) {
        // Purpose can name the wrongly selected template. It cannot supply missing business facts.
        String facts = factors.facts().replaceAll("[^\\S\\n]+", "");
        Set<Business> actual = EnumSet.noneOf(Business.class);
        for (var business : Business.values()) if (supported(business.source, facts)) actual.add(business);
        if (actual.isEmpty()) return; // Unknown is not a positive classification.

        String fixed = blocks.stream().map(TemplateLayout.Block::text)
            .map(text -> text.replaceAll("\\{\\{[^{}]*}}", "").replaceAll("[^\\S\\n]+", ""))
            .reduce("", (a, b) -> a + "\n" + b);
        Set<Business> required = EnumSet.noneOf(Business.class);
        for (var business : Business.values()) if (business.template.matcher(fixed).find()) required.add(business);
        // A specific letter title is stronger than incidental cost clauses in its body.
        Set<Business> titled = EnumSet.noneOf(Business.class);
        for (String line : fixed.split("\\n")) if (line.length() <= 60 && line.matches(".*(?:函|通知书?)$")) {
            for (var business : Business.values()) if (business.template.matcher(line).find()) titled.add(business);
        }
        if (!titled.isEmpty()) required = titled;
        if (required.isEmpty() || !Collections.disjoint(actual, required)) return;

        throw new DraftingException("本次材料属于" + labels(actual) + "业务，当前模板包含" + labels(required)
            + "业务内容，两者不匹配。请改选与材料相符的模板，或选择“标准起草（不使用上传模板）”；如需起草当前模板，请补充对应业务的合同、履行情况和具体诉求", 422);
    }

    private static boolean supported(Pattern pattern, String facts) {
        for (String clause : facts.split("[。；，,\\n]")) {
            var match = pattern.matcher(clause);
            while (match.find()) {
                String before = clause.substring(Math.max(0, match.start() - 16), match.start());
                if (!before.matches(".*(?:未提供|未附|没有提供|不涉及|并非|不是|不存在|不包含|缺少|缺失|示例|举例|模板参考).{0,12}")) return true;
            }
        }
        return false;
    }

    private static String labels(Set<Business> values) {
        return values.stream().map(business -> business.label).reduce((a, b) -> a + "、" + b).orElse("");
    }
}
