package org.ruoyi.service.legal;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.response.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class DraftingFactsTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    @Test void customLoanPurposeStillRequiresAllExplicitArrearsComponents()throws Exception{
        String r="请起草律师函\n---起草要素---\n"+JSON.writeValueAsString(new DraftingFacts.Factors("甲乙","个人贷款。已到期逾期本金合计5000元。未付正常利息合计239.17元。本息合计5239.17元。逾期罚息合计18.80元。本次催收总额5257.97元。剩余本金70000元（含已到期本金）。","催告已到期款项",""));
        String complete="逾期本金5000.00元，正常利息239.17元，本息合计5239.17元，逾期罚息18.80元，本次催收总额5257.97元。";
        assertDoesNotThrow(()->DraftingFacts.requireGrounded(complete,r));
        assertThrows(DraftingException.class,()->DraftingFacts.requireGrounded(complete.replace("本息合计5239.17元，",""),r));
        assertThrows(DraftingException.class,()->DraftingFacts.requireGrounded(complete.replace("18.80元","18.81元"),r));
        assertThrows(DraftingException.class,()->DraftingFacts.requireGrounded(complete+"本所系常年法律顾问。",r));
        assertThrows(DraftingException.class,()->DraftingFacts.requireGrounded(complete+"剩余未到期本金70000元。",r));
        assertThrows(DraftingException.class,()->DraftingFacts.requireGrounded(complete+"剩余本金70000元（含未到期本金）不在本次催收范围。",r));
        assertDoesNotThrow(()->DraftingFacts.requireGrounded(complete+"剩余本金70000元，其中未到期本金65000元不在本次催收范围。",r));
        String limited=r.replace("催告已到期款项","仅主张已到期款项");
        assertThrows(DraftingException.class,()->DraftingFacts.requireGrounded(complete+"委托人将起诉要求清偿全部剩余未还本金。",limited));
        assertDoesNotThrow(()->DraftingFacts.requireGrounded(complete+"本函不要求清偿全部剩余未还本金。",limited));
        String secured=r.replace("个人贷款。","个人贷款。主合同《个人贷款合同》编号MAIN-123，另有抵押合同。");
        assertThrows(DraftingException.class,()->DraftingFacts.requireGrounded(complete,secured));
        assertDoesNotThrow(()->DraftingFacts.requireGrounded(complete+"主借款合同MAIN-123，另签抵押合同。",secured));
        String dated=r.replace("个人贷款。","个人贷款。首期逾期起算日：2026年10月2日。核算截止日：2026年10月22日。拟定发函日期：2026年10月22日。催告届满日：2026年10月29日。");
        assertThrows(DraftingException.class,()->DraftingFacts.requireGrounded(complete,dated));
        assertDoesNotThrow(()->DraftingFacts.requireGrounded(complete+"逾期2026年10月2日起，核算、发函日期2026年10月22日，催告至2026年10月29日。",dated));
    }
    static String request()throws Exception{
        return "请起草律师函\n---起草要素---\n"+JSON.writeValueAsString(new DraftingFacts.Factors("甲方：张某\n乙方：乙公司","房屋租赁合同，签约时间2024年06月01日；月租30元，签订时已收3个月租金90元，设备押金100元。","依据所导入合同的权利义务约定起草律师函。具体诉求和履行情况待核实，不得认定已发生违约。","租期自2024年6月1日至2027年5月31日，共36个月"));
    }
    static String complete(){return "律师函\n致：【收件人待确认】\n合同双方为张某、乙公司，2024年6月1日签订房屋租赁合同，租期至2027年5月31日。月租30元，签订时已收三个月租金90元，设备押金100元。\n合同未记载编号；当前履行情况和具体诉求【待补充】，不能认定已构成违约。\n【承办律师待补充】\n电话：【联系电话待补充】\n【发函日期待确认】";}
    @Test void suppliedContractTermsMustNotBeLostOrTurnedIntoLoanDebt()throws Exception{
        String request=request();assertNotNull(DraftingFacts.input(request));assertDoesNotThrow(()->DraftingFacts.requireGrounded(complete(),request));
        assertThrows(DraftingException.class,()->DraftingFacts.requireGrounded("根据房屋租赁合同，您拖欠个人一手房按揭贷款，贷款本金 元。",request));
        assertThrows(DraftingException.class,()->DraftingFacts.requireGrounded(complete().replace("90元","【待补充】"),request));
        assertThrows(DraftingException.class,()->DraftingFacts.requireGrounded(complete().replace("张某","甲方"),request));
        assertThrows(DraftingException.class,()->DraftingFacts.requireGrounded(complete().replace("2027年5月31日","【待补充】"),request));
        assertThrows(DraftingException.class,()->DraftingFacts.requireGrounded(complete()+"\n此款经多次索要，无理由拒付。",request));
        assertThrows(DraftingException.class,()->DraftingFacts.requireGrounded(complete()+"\n一切经济损失，包括保全费、律师代理费等，概由您自行承担。",request));
        assertDoesNotThrow(()->DraftingFacts.requireGrounded(complete().replace("30元","30.00元").replace("2024年6月1日","2024年06月01日"),request));
    }
    @Test void missingContactAndLetterDatesMustBeExplicitInsteadOfSilentBlanks()throws Exception{
        for(String blank:List.of("2026年  月  日","电话：   ","  律师","贷款本金 元"))assertThrows(DraftingException.class,()->DraftingFacts.requireGrounded(complete()+"\n"+blank,request()));
        String narrow=request().replace("依据所导入合同的权利义务约定起草律师函。具体诉求和履行情况待核实，不得认定已发生违约。","只提醒核对押金约定");
        assertDoesNotThrow(()->DraftingFacts.requireGrounded("请核对合同的设备押金约定。",narrow));
        String unknownDirection=request().replace("具体诉求和履行情况待核实，不得认定已发生违约。","具体诉求、实际履行情况及发函方、收件人由委托人核实补充。");
        assertThrows(DraftingException.class,()->DraftingFacts.requireGrounded(complete().replace("【收件人待确认】","乙公司"),unknownDirection));
        assertDoesNotThrow(()->DraftingFacts.requireGrounded(complete(),unknownDirection));
        assertThrows(DraftingException.class,()->DraftingFacts.requireGrounded(complete()+"\n本所接受张某（甲方）的委托。",unknownDirection));
    }
    @Test void templateRepairPublishesOnlyTheValidatedResultAndRetriesAtMostOnce()throws Exception{
        String id="version";String request=request()+"\n---版式填写JSON开始---\n"+JSON.writeValueAsString(new TemplateDrafting.Document(id,List.of(new TemplateLayout.Block("p0","律师函",false),new TemplateLayout.Block("p1","固定前缀：{{正文}}",true))))+"\n---版式填写JSON结束---";
        String bad=JSON.writeValueAsString(new TemplateDrafting.Document(id,List.of(new TemplateLayout.Block("p1","改写固定模板原文",true))));
        String good=JSON.writeValueAsString(new TemplateDrafting.Document(id,List.of(new TemplateLayout.Block("p1","固定前缀："+complete(),true))));
        // A template never adds missing-field annotations that were absent from its original text.
        good=good.replace("【收件人待确认】","").replace("【待补充】","").replace("【承办律师待补充】","").replace("【联系电话待补充】","").replace("【发函日期待确认】","");
        final String corrected=good;
        assertTrue(TemplateDrafting.modelInput(request).startsWith(request));
        assertTrue(TemplateDrafting.modelInput(request).contains("calculations"));
        for(boolean repaired:List.of(true,false)){
            var events=new ArrayList<String>();var calls=new AtomicInteger();
            var sink=new StreamingChatResponseHandler(){public void onPartialResponse(String text){events.add(text);}public void onCompleteResponse(ChatResponse value){events.add("done");}public void onError(Throwable error){events.add("error");}};
            var wrapped=TemplateDrafting.wrap(sink,request,(problem,retry)->{calls.incrementAndGet();assertTrue(problem.contains("模板原文"));retry.onPartialResponse(repaired?corrected:bad);retry.onCompleteResponse(null);});
            wrapped.onPartialResponse(bad);assertTrue(events.isEmpty());wrapped.onCompleteResponse(null);wrapped.onCompleteResponse(null);
            assertEquals(1,calls.get());
            if(repaired){assertEquals(2,events.size());assertEquals("done",events.get(1));assertTrue(events.get(0).contains("固定前缀"));}else assertEquals(List.of("error"),events);
        }
    }
    @Test void exportRejectsOldIncorrectLettersInEveryFormat()throws Exception{
        var conversion=new DocumentConversion();try{
            var exporter=new DocumentExport(new TemplateFiles(null,conversion),new DocumentWord(),conversion);
            for(String format:List.of("docx","pdf","md","txt"))assertThrows(DraftingException.class,()->exporter.generate("律师函","您拖欠我行个人贷款。",format,request(),"standard"));
        }finally{conversion.close();}
    }
}
