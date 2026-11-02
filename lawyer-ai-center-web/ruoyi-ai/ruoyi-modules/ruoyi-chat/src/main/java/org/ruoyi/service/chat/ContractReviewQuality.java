package org.ruoyi.service.chat;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;
/** Conservative arithmetic check; does not decide enforceability or actual loss. */
public final class ContractReviewQuality {
    private ContractReviewQuality() {}
    private static final Pattern PRICE=Pattern.compile("(?:合同总价|合同价款|总价款|标的总额|合同总金额)\\s*[为是：:]?\\s*(?:人民币)?\\s*([0-9][0-9,，]*(?:\\.[0-9]+)?)\\s*(万)?元");
    private static final Pattern PENALTY=Pattern.compile("违约金\\s*[为是：:]?\\s*(?:人民币)?\\s*([0-9][0-9,，]*(?:\\.[0-9]+)?)\\s*(万)?元");
    private static final Pattern COMPARISON=Pattern.compile("(?:远超|远远超过|大幅超过|显著超过).{0,12}(?:合同标的|标的额|合同价款|合同总价|总价款)");
    private static Set<BigDecimal> amounts(Pattern pattern,String source) {
        Set<BigDecimal> values=new HashSet<>();var match=pattern.matcher(Objects.toString(source,""));
        while(match.find()){var value=new BigDecimal(match.group(1).replace(",","").replace("，",""));if(match.group(2)!=null)value=value.multiply(BigDecimal.valueOf(10000));values.add(value.stripTrailingZeros());}return values;
    }
    public static String apply(String raw,String source,boolean enabled) {
        if(!enabled)return raw;
        try {
            var value=(ObjectNode)ContractReviewReport.parse(raw);var prices=amounts(PRICE,source);var penalties=amounts(PENALTY,source);int count=0;
            for(var node:value.path("issues")) {
                var issue=(ObjectNode)node;String problem=issue.path("problem").asText();
                if(!COMPARISON.matcher(problem).find())continue;
                boolean known=prices.size()==1&&penalties.size()==1;
                if(known&&penalties.iterator().next().compareTo(prices.iterator().next())>0)continue;
                issue.put("problem",known?"金额关系校验提示：已识别违约金不高于合同总价，原说明中的“远超标的额”不成立。违约金是否合理仍需结合实际损失、履约情况等人工判断。":"金额关系校验提示：材料中的金额关系无法唯一确定，原说明中的超额判断待核实。");
                issue.putArray("qualityWarnings").add("请人工核对金额关系");count++;
            }
            if(count==0)return raw;
            LoggerFactory.getLogger(ContractReviewQuality.class).info("review_quality rule=penalty_vs_price flagged={}",count);
            return value.toString();
        }catch(Exception ignored){return raw;}
    }
}
