package org.ruoyi.service.retrieval;

import java.util.LinkedHashSet;

/** Map common colloquial legal questions to statutory wording for Chinese FULLTEXT recall. */
public final class LegalSearchTerms {
    private LegalSearchTerms() {}
    public static String keywords(String query) {
        if (query == null) return "";
        var terms = new LinkedHashSet<String>();
        if (query.matches("(?s).*(诉讼时效|时效多久|时效多长|起算|中断|中止).*")) {
            terms.add("诉讼时效 三年 知道 义务人");
            if (query.contains("中断")) terms.add("重新计算");
            if (query.contains("中止")) terms.add("最后六个月");
        }
        if (query.matches("(?s).*(二倍|双倍|没签|未签|未订立书面).*"))
            terms.add("二倍 工资 书面 劳动合同 不可抗力 故意 重大过失");
        if (query.matches("(?s).*(逾期|拖欠|不付款|不付钱|违约责任|违约金).*"))
            terms.add("不履行 合同义务 违约责任 金钱债务 违约金");
        if (query.matches("(?s).*(租房|房租|房屋租赁|押金|转租).*"))
            terms.add("租赁 租金 转租 返还");
        if (query.matches("(?s).*(辞退|违法解除|解雇|离职赔偿).*"))
            terms.add("解除 劳动合同 经济补偿 赔偿金");
        // Unrecognised questions keep their exact query; no fabricated provisions are injected.
        return terms.isEmpty() ? query : String.join(" ", terms);
    }
}
