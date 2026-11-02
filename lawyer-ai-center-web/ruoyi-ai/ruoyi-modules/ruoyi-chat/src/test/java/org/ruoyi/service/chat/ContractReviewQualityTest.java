package org.ruoyi.service.chat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
@org.junit.jupiter.api.Tag("dev")
class ContractReviewQualityTest {
    private final String raw="{\"summary\":\"风险\",\"issues\":[{\"clauseTitle\":\"违约金\",\"quote\":\"违约金3001元\",\"problem\":\"违约金远超合同标的额\",\"suggestion\":\"核对损失\"}]}";
    @Test void equalityIsFlaggedAndReviewPreserved(){var output=ContractReviewQuality.apply(raw,"合同总价3001元，违约金3001元。",true);assertTrue(output.contains("请人工核对金额关系"));assertTrue(output.contains("不成立"));assertTrue(output.contains("核对损失"));}
    @Test void highPenaltyIsNotFlagged(){assertEquals(raw,ContractReviewQuality.apply(raw,"合同总价3001元，违约金9000元。",true));}
    @Test void lowPenaltyAndUnitConversion(){assertTrue(ContractReviewQuality.apply(raw,"合同总价1万元，违约金3,001元。",true).contains("请人工核对金额关系"));}
    @Test void ambiguousAmountsRequireManualPairing(){assertTrue(ContractReviewQuality.apply(raw,"合同总价3001元；违约金3001元或违约金9000元。",true).contains("无法唯一确定"));}
    @Test void lossComparisonAndNormalStatementsUntouched(){String text=raw.replace("远超合同标的额","可能过高，需对照实际损失");assertEquals(text,ContractReviewQuality.apply(text,"合同总价3001元，违约金3001元。",true));}
    @Test void disabledAndMalformedPreserved(){assertEquals(raw,ContractReviewQuality.apply(raw,"合同总价3001元，违约金3001元。",false));assertEquals("text",ContractReviewQuality.apply("text","",true));}
}
