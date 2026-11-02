package org.ruoyi.service.legal;

import org.junit.jupiter.api.*;
import org.ruoyi.domain.vo.knowledge.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class FactSearchRelevanceTest {
    FactElements elements(String cause,String... terms){return new FactElements(List.of(cause),List.of(terms),List.of(),List.of(),List.of());}
    KnowledgeRetrievalVo hit(String id,String name,String body,double score){var hit=new KnowledgeRetrievalVo();hit.setId(id);hit.setSourceName(name);hit.setContent(body);hit.setScore(score);hit.setReference(new LegalReference("law",name,"12","effective",null,null,null,null,id));return hit;}
    @Test void loanPrincipalBeatsUnrelatedLabourEvenWithLargeCrossLibraryScore(){
        var input=elements("民间借贷纠纷","返还本金","借条");
        var irrelevant=hit("labour","中华人民共和国劳动合同法","劳动者违反服务期支付违约金",9999);
        var direct=hit("loan","中华人民共和国民法典","借款人应当按照约定的期限返还借款",0);
        assertEquals("loan",FactSearchRelevance.rank(input,List.of(irrelevant,direct)).get(0).getId());
        assertEquals(9999,irrelevant.getScore());assertEquals("loan",direct.getReference().fragmentId());
    }
    @Test void labourClaimsKeepLabourLawAndEconomicCompensation(){
        var input=elements("劳动合同纠纷","拖欠工资","经济补偿");
        var loan=hit("loan","借贷规定","借款本金返还",1);
        var labour=hit("labour","中华人民共和国劳动合同法","解除劳动合同经济补偿工资劳动报酬",0);
        assertEquals(labour,FactSearchRelevance.rank(input,List.of(loan,labour)).get(0));
    }
    @Test void principalRequestPrioritisesRepaymentRatherThanUnrequestedInterest(){
        var input=elements("民间借贷纠纷","返还借款","本金");
        var repayment=hit("repay","民法典","借款人应当按照约定的期限返还借款",0);
        var interest=hit("interest","借款合同：自然人借款利息","民间借贷借款利息利率计息还款",1);
        assertEquals(repayment,FactSearchRelevance.rank(input,List.of(interest,repayment)).get(0));
        assertFalse(FactSearchRelevance.terms(input).contains("利息"));
        assertEquals(List.of("到期还本"),FactSearchRelevance.explain(input,repayment).matchedFocus());
    }
    @Test void explicitSaleCancellationAndRestitutionBeatGenericOwnershipNotes(){
        var input=elements("买卖合同纠纷","未交货","解除合同","返还货款");
        var remedy=hit("remedy","民法典","合同解除后可以请求恢复原状",0);
        var other=hit("other","买卖合同：权利瑕疵","买卖出卖人买受人交付货款第三人权利",1);
        assertEquals(remedy,FactSearchRelevance.rank(input,List.of(other,remedy)).get(0));
        assertTrue(FactSearchRelevance.terms(input).contains("恢复原状"));
    }
    @Test void mixedLabourAndLoanFactsAreNotPenalisedAsForeignTopics(){
        var input=elements("劳动与借款纠纷","工资","借款");
        var labour=hit("labour","劳动合同法","工资支付",0);
        var detail=FactSearchRelevance.explain(input,labour);assertTrue(detail.domain()>=0);assertEquals(2,detail.inputDomains().size());
    }
    @Test void leaseRepairAndSaleDeliveryReceiveSpecificLexicalRecall(){
        assertTrue(FactSearchRelevance.query(elements("租赁合同纠纷","房屋维修")).contains("出租人 承租人 租金 维修"));
        assertTrue(FactSearchRelevance.terms(elements("买卖合同纠纷","迟延交货")).contains("出卖人"));
        var input=elements("租赁合同纠纷","维修费用","减免租金");
        var direct=hit("repair","民法典","出租人维修，承租人自行维修费用由出租人负担并减少租金",0);
        assertEquals(direct,FactSearchRelevance.rank(input,List.of(hit("sale","买卖合同","交货违约",1),direct)).get(0));
    }
    @Test void exactLawAndArticleHintsAreExplainableWithoutInventingCitations(){
        var input=new FactElements(List.of(),List.of("第12条"),List.of(),List.of(),List.of("劳动合同法"));
        var direct=hit("law","劳动合同法 第12条","正文",0);var explanation=FactSearchRelevance.explain(input,direct);
        assertEquals(0.45,explanation.metadata(),0.0001);assertTrue(explanation.matchedTerms().contains("第12条"));
        assertFalse(FactSearchRelevance.query(elements("民间借贷","借款")).contains("民法典"));
    }
    @Test void equivalentEvidenceKeepsOriginalOrderAndNoDefaultStatusFilterIsIntroduced(){
        var a=hit("a","测试法规","相同正文",0);var b=hit("b","测试法规","相同正文",0);
        b.setReference(new LegalReference("law","测试法规","12","repealed",null,null,null,null,"b"));
        assertEquals(List.of(b,a),FactSearchRelevance.rank(elements("未知案由","独特词"),List.of(b,a)));
        assertEquals("repealed",b.getReference().status());assertTrue(new FactSearchKnowledge.Filter("all","all").accepts(b));
    }
    @Test void emptyQueryDoesNotBecomeAMadeUpTopicAndLargeTermSetsStayBounded(){
        assertTrue(FactSearchRelevance.query(new FactElements(List.of(),List.of(),List.of(),List.of(),List.of())).isEmpty());
        assertEquals("合同",FactSearchRelevance.query(new FactElements(List.of(),List.of("合同"),List.of(),List.of(),List.of())));
        assertTrue(FactSearchRelevance.terms(elements("劳动与借贷租赁买卖纠纷","工资","利息","维修")).size()<=32);
    }
}
