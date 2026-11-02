package org.ruoyi.service.legal;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.response.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TemplateFillReportTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static TemplateLayout.Block slot(String id,String label){
        String text=label+"：{{"+label+"}}";int start=label.length()+1;
        var field=new TemplateFields.Field(id+"-field",id,start,text.length(),text.substring(start),label,"text","","text",true);
        return new TemplateLayout.Block(id,text,true,List.of(field));
    }
    private static String request(List<TemplateLayout.Block> blocks)throws Exception{
        // The number is present in prose, not an explicit same-name label: this fixture
        // exercises model omissions independently of deterministic label prefill.
        return "---起草要素---\n"+JSON.writeValueAsString(new DraftingFacts.Factors("合成主体","客户名称：合成公司。合同记录的编号为TEST-CUSTOMER-1。","仅填写本次资料",""))
            +"\n---版式填写JSON开始---\n"+JSON.writeValueAsString(new TemplateDrafting.Document("test-version",blocks))+"\n---版式填写JSON结束---";
    }
    private static String answer(boolean includeNumber){
        return "{\"documentTemplate\":\"test-version\",\"fieldValues\":[{\"fieldId\":\"p0-field\",\"value\":\"合成公司\",\"quote\":\"客户名称：合成公司。\"}"
            +(includeNumber?",{\"fieldId\":\"p1-field\",\"value\":\"TEST-CUSTOMER-1\",\"quote\":\"合同记录的编号为TEST-CUSTOMER-1。\"}":"")+"]}";
    }
    @Test void reportsOmittedNonFinancialFieldsInsteadOfCallingAPlaceholderComplete()throws Exception{
        String request=request(List.of(slot("p0","客户名称"),slot("p1","客户编号")));
        var document=TemplateDrafting.read(TemplateDrafting.finish(answer(false),request));
        assertEquals("partial",document.fillReport().status());assertEquals(2,document.fillReport().totalFields());assertEquals(1,document.fillReport().filledFields());
        assertEquals("客户编号",document.fillReport().issues().get(0).label());
        assertEquals("complete",TemplateDrafting.read(TemplateDrafting.finish(answer(true),request)).fillReport().status());
    }
    @Test void oldSnapshotWithAnOmittedCaptionReportsConfigurationRatherThanInventingAField()throws Exception{
        var block=new TemplateLayout.Block("p0","致：",true,List.of());
        var report=TemplateFillReport.assess(List.of(block),List.of(block),List.of());
        assertEquals("configuration_required",report.status());assertEquals("template_configuration",report.issues().get(0).reason());
        assertEquals("致：",block.text());
    }
    @Test void disabledCaptionIsNotReenabledOrReportedAsAMissingConfiguration(){
        var field=new TemplateFields.Field("p0-caption","p0",2,2,"","收件人","text","","text",false);
        var block=new TemplateLayout.Block("p0","致：",true,List.of(field));
        var report=TemplateFillReport.assess(List.of(block),List.of(block),null);
        assertEquals("complete",report.status());assertEquals(0,report.totalFields());
    }
    @Test void reportsAnOmittedSemanticBlankEvenWhenTheSameBlockHasOtherConfiguredFields(){
        var field=new TemplateFields.Field("p0-name","p0",3,7,"____","姓名","text","","underline",true);
        var block=new TemplateLayout.Block("p0","姓名：____；电话：   ",true,List.of(field));
        var filled=new TemplateLayout.Block("p0","姓名：合成甲；电话：   ",true);
        var report=TemplateFillReport.assess(List.of(block),List.of(filled),null);
        assertEquals("configuration_required",report.status());
        assertTrue(report.issues().stream().anyMatch(issue->"电话".equals(issue.label())&&"template_configuration".equals(issue.reason())));
    }
    @Test void recomputesCoverageOnSaveRatherThanTrustingAForgedCompleteReport()throws Exception{
        String request=request(List.of(slot("p0","客户名称"),slot("p1","客户编号")));
        var partial=JSON.readTree(TemplateDrafting.finish(answer(false),request));
        ((com.fasterxml.jackson.databind.node.ObjectNode)partial.path("fillReport")).put("status","complete");
        assertEquals("partial",TemplateDrafting.read(TemplateDrafting.finish(partial.toString(),request,true)).fillReport().status());
    }
    @Test void repairsAnOmissionOnlyOnceAndStillReportsActualMissingData()throws Exception{
        String request=request(List.of(slot("p0","客户名称"),slot("p1","客户编号")));
        List<String> results=new ArrayList<>();AtomicInteger retries=new AtomicInteger();
        var delegate=new StreamingChatResponseHandler(){public void onPartialResponse(String value){results.add(value);}public void onCompleteResponse(ChatResponse r){}public void onError(Throwable error){fail(error);}};
        var wrapped=TemplateDrafting.wrap(delegate,request,(problem,handler)->{retries.incrementAndGet();handler.onPartialResponse(answer(false));handler.onCompleteResponse(null);});
        wrapped.onPartialResponse(answer(false));wrapped.onCompleteResponse(null);
        assertEquals(1,retries.get());assertEquals(1,results.size());assertEquals("partial",TemplateDrafting.read(results.get(0)).fillReport().status());
    }
    @Test void synchronousRepairFailureTerminatesOnceInsteadOfLeavingTheStreamHanging()throws Exception{
        String request=request(List.of(slot("p0","客户名称"),slot("p1","客户编号")));
        AtomicInteger errors=new AtomicInteger(),contents=new AtomicInteger();
        var delegate=new StreamingChatResponseHandler(){public void onPartialResponse(String value){contents.incrementAndGet();}public void onCompleteResponse(ChatResponse r){fail("Unexpected success");}public void onError(Throwable error){errors.incrementAndGet();assertInstanceOf(IllegalStateException.class,error);}};
        var wrapped=TemplateDrafting.wrap(delegate,request,(problem,handler)->{throw new IllegalStateException("synthetic model unavailable");});
        wrapped.onPartialResponse(answer(false));wrapped.onCompleteResponse(null);wrapped.onError(new IllegalStateException("duplicate callback"));
        assertEquals(1,errors.get());assertEquals(0,contents.get());
    }
    @Test void explicitAbstentionNeverBecomesTextAndDoesNotDiscardOtherGroundedFields()throws Exception{
        String request=request(List.of(slot("p0","客户名称"),slot("p1","客户编号")));
        String response=answer(false).replace("]}",",{\"fieldId\":\"p1-field\",\"value\":\"【待补充】\",\"quote\":\"【待补充】\"}]}");
        var document=TemplateDrafting.read(TemplateDrafting.finish(response,request));
        assertEquals("partial",document.fillReport().status());assertEquals(1,document.fillReport().filledFields());
        assertTrue(document.blocks().get(0).text().contains("合成公司"));
        assertEquals("客户编号：{{客户编号}}",document.blocks().get(1).text());
        assertFalse(TemplateDrafting.plain(document).contains("【待补充】"));
        String duplicate=response.replace("]}",",{\"fieldId\":\"p1-field\",\"value\":\"\",\"quote\":\"\"}]}");
        assertThrows(DraftingException.class,()->TemplateDrafting.finish(duplicate,request));
        String unsupported=response.replace("\"value\":\"【待补充】\"","\"value\":\"FORGED-CUSTOMER\"");
        assertThrows(DraftingException.class,()->TemplateDrafting.finish(unsupported,request));
    }
    @Test void aGroundedFullBankNameDoesNotDuplicateTheImmutableRoleSuffix()throws Exception{
        String text="接受{{委托人}}行委托。";
        var field=new TemplateFields.Field("p0-client","p0",2,9,"{{委托人}}","委托人","text","","text",true);
        var block=new TemplateLayout.Block("p0",text,true,List.of(field));
        String request="---起草要素---\n"+JSON.writeValueAsString(new DraftingFacts.Factors("","本次来源记载合成商业银行。","",""))+"\n---版式填写JSON开始---\n"+JSON.writeValueAsString(new TemplateDrafting.Document("test-version",List.of(block)))+"\n---版式填写JSON结束---";
        String response="{\"documentTemplate\":\"test-version\",\"fieldValues\":[{\"fieldId\":\"p0-client\",\"value\":\"合成商业银行\",\"quote\":\"本次来源记载合成商业银行。\"}]}";
        assertEquals("接受合成商业银行委托。",TemplateDrafting.read(TemplateDrafting.finish(response,request)).blocks().get(0).text());
    }
}
