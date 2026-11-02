package org.ruoyi.service.legal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.apache.poi.xwpf.usermodel.*;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StrictTemplateTest {
    @Test void propertyTemplatesMatchIndependentOriginalFieldManifest()throws Exception{
        var root=java.nio.file.Path.of("artifacts/20261023/物业模板真实解析核验");
        var manifest=new ObjectMapper().readTree(root.resolve("original-manifest.json").toFile());
        List<Map<String,Object>> evidence=new ArrayList<>();
        assertEquals(13,manifest.size());
        for(var file:manifest){
            var path=java.nio.file.Path.of("artifacts/20261022/物业服务合同20261022").resolve(file.path("filename").asText());
            if(!java.nio.file.Files.exists(path))path=java.nio.file.Path.of("artifacts/3、文书模板").resolve(file.path("filename").asText());
            byte[] source=java.nio.file.Files.readAllBytes(path);
            var actual=TemplateLayout.inspect(source,true);
            Map<String,TemplateLayout.Block> indexed=new HashMap<>();actual.forEach(b->indexed.put(b.id(),b));
            int expectedCount=0;
            for(var expected:file.path("blocks")){
                var id=expected.path("id").asText();var block=indexed.get(id);
                assertNotNull(block,file.path("filename").asText()+":"+id);
                assertEquals(expected.path("text").asText(),block.text());
                Set<String> expectedSpans=new HashSet<>();
                for(var f:expected.path("expectedFields"))expectedSpans.add(f.path("start").asInt()+":"+f.path("end").asInt()+":"+f.path("original").asText());
                Set<String> spans=new HashSet<>();
                for(var f:block.fields()){
                    assertEquals(f.original(),block.text().substring(f.start(),f.end()));
                    assertTrue(spans.add(f.start()+":"+f.end()+":"+f.original()),"Duplicate source position");
                }
                assertEquals(expectedSpans,spans,file.path("filename").asText()+":"+id);
                expectedCount+=expectedSpans.size();
            }
            assertEquals(expectedCount,actual.stream().mapToInt(b->b.fields().size()).sum());
            assertArrayEquals(source,java.nio.file.Files.readAllBytes(path));
            evidence.add(Map.of("file",file.path("filename").asText(),"fields",expectedCount,"pass",true));
        }
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(root.resolve("production-manifest-check.json").toFile(),evidence);
    }
    @Test void recipientHeadingHasExactlyOneRealSourceField()throws Exception{
        byte[] source;
        try(var doc=new XWPFDocument();var out=new ByteArrayOutputStream()){
            doc.createParagraph().createRun().setText("________先生/女士：");
            doc.createParagraph().createRun().setText("姓名：");
            doc.createParagraph().createRun().setText("  固定条款和缩进不用于填写。  ");
            doc.write(out);source=out.toByteArray();
        }
        var blocks=TemplateLayout.inspect(source,true);
        assertEquals(1,blocks.get(0).fields().size());
        var f=blocks.get(0).fields().get(0);
        assertEquals(0,f.start());assertEquals(8,f.end());assertEquals("________",f.original());assertEquals("收件人",f.label());
        var caption=blocks.get(1).fields().get(0);
        assertEquals("姓名",caption.label());assertEquals(3,caption.start());assertEquals(3,caption.end());
        assertDoesNotThrow(()->TemplateFields.validate(blocks,List.of(caption)));
        assertTrue(blocks.get(2).fields().isEmpty());
        assertEquals("________先生/女士：",blocks.get(0).text());
        assertThrows(DraftingException.class,()->TemplateFields.validate(blocks,List.of(f,new TemplateFields.Field("fake","p0",14,14,"","收件人","text","","text",true))));
        assertThrows(DraftingException.class,()->TemplateFields.validate(blocks,List.of(f,new TemplateFields.Field("same-span","p0",0,8,"________","重复字段","text","","underline",true))));
        var evidence=java.nio.file.Path.of("artifacts/20261023/原模板字段识别/source-span-heading.json");
        java.nio.file.Files.createDirectories(evidence.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(evidence.toFile(),blocks);
    }
    @Test void continuousUnderlinedBlankAcrossRunsIsOneFieldAndRepeatedParagraphsRemainDistinct()throws Exception{
        byte[] source;
        try(var doc=new XWPFDocument();var out=new ByteArrayOutputStream()){
            for(int i=0;i<2;i++){
                var p=doc.createParagraph();p.createRun().setText("本金：");
                for(int j=0;j<3;j++){var r=p.createRun();r.setUnderline(UnderlinePatterns.SINGLE);r.setText(" ");}
                p.createRun().setText("元");
            }
            doc.write(out);source=out.toByteArray();
        }
        var blocks=TemplateLayout.inspect(source,true);
        assertEquals(2,blocks.size());
        for(var b:blocks){assertEquals(1,b.fields().size());var f=b.fields().get(0);assertEquals(3,f.start());assertEquals(6,f.end());assertEquals("   ",f.original());assertEquals("元",f.unit());assertEquals("underline",f.presentation());assertEquals(f.original(),b.text().substring(f.start(),f.end()));}
        assertNotEquals(blocks.get(0).fields().get(0).id(),blocks.get(1).fields().get(0).id());
        var logical=TemplateFields.logical(blocks);assertEquals("本金：{{本金}}元",logical.get(0).text());
    }
    @Test void onlyRealEmptyTableCellsAllowZeroLengthAndCannotDuplicateWithDifferentIds()throws Exception{
        byte[] source;
        try(var doc=new XWPFDocument();var out=new ByteArrayOutputStream()){
            var table=doc.createTable(1,2);table.getRow(0).getCell(0).setText("姓名");
            doc.write(out);source=out.toByteArray();
        }
        var blocks=TemplateLayout.inspect(source,true);
        var empty=blocks.stream().filter(b->b.text().isEmpty()).findFirst().orElseThrow();
        assertEquals(1,empty.fields().size());var field=empty.fields().get(0);
        assertDoesNotThrow(()->TemplateFields.validate(blocks,List.of(field)));
        var duplicate=new TemplateFields.Field("other-id",field.blockId(),0,0,"","姓名","text","","text",true);
        assertThrows(DraftingException.class,()->TemplateFields.validate(blocks,List.of(field,duplicate)));
    }
    @Test void sidecarFieldsPreserveUnderlineUnitsAndFixedRuns()throws Exception{
        byte[] source;
        try(var doc=new XWPFDocument();var out=new ByteArrayOutputStream()){
            doc.createParagraph().createRun().setText("律师函");
            var p=doc.createParagraph();p.createRun().setText("逾期本金");var blank=p.createRun();blank.setFontFamily("仿宋");blank.setText("________");p.createRun().setText("元；正常利息  元。");
            doc.write(out);source=out.toByteArray();
        }
        var originals=TemplateLayout.inspect(source,true).stream().map(b->new TemplateLayout.Block(b.id(),b.text(),true,b.fields())).toList();
        byte[] stored=TemplateLayout.storeFields(source,originals);
        assertEquals(originals,TemplateLayout.storedFields(stored,List.of()));
        assertEquals(TemplateLayout.inspect(source),TemplateLayout.inspect(stored));
        assertEquals("逾期本金________元；正常利息  元。",originals.get(1).text());
        var logical=TemplateFields.logical(originals);
        assertEquals("逾期本金{{逾期本金}}元；正常利息{{正常利息}}元。",logical.get(1).text());
        String request="---起草要素---\n"+new ObjectMapper().writeValueAsString(new DraftingFacts.Factors("甲乙","已到期逾期本金合计1000.00元。未付正常利息合计30.00元。","催收",""))+request(logical);
        var result=TemplateDrafting.read(TemplateDrafting.finish(new ObjectMapper().writeValueAsString(new TemplateDrafting.Document("version",logical)),request));
        assertEquals("逾期本金1000.00元；正常利息30.00元。",result.blocks().get(1).text());
        var output=TemplateLayout.fill(source,result.blocks(),originals);
        try(var doc=new XWPFDocument(new ByteArrayInputStream(output))){
            var p=doc.getParagraphs().get(1);assertEquals("逾期本金1000.00 元；正常利息30.00元。",p.getText());
            assertTrue(p.getRuns().stream().anyMatch(r->r.text().startsWith("1000.00")&&r.getUnderline()!=UnderlinePatterns.NONE));
            assertEquals("仿宋",p.getRuns().stream().filter(r->r.text().startsWith("1000.00")).findFirst().orElseThrow().getFontFamily());
        }
        assertThrows(DraftingException.class,()->TemplateLayout.fill(source,List.of(result.blocks().get(0),new TemplateLayout.Block("p1","逾期本金1000.00万元；正常利息30.00元。",true)),originals));
        var f=originals.get(1).fields().get(0);
        assertThrows(DraftingException.class,()->TemplateFields.validate(originals,List.of(new TemplateFields.Field(f.id(),f.blockId(),0,f.end(),"逾期本金________",f.label(),f.kind(),f.unit(),f.presentation(),true))));
    }
    @Test void originalUploadedLetterCanBeMappedWithoutEditingItsText()throws Exception{
        var file=java.nio.file.Path.of("artifacts/20261021/律 师 函（模板）.docx");if(!java.nio.file.Files.exists(file))return;
        byte[] source=java.nio.file.Files.readAllBytes(file);var blocks=TemplateLayout.inspect(source,true);
        // A standalone recipient caption is a server-identified insertion anchor.
        assertTrue(blocks.stream().filter(b->b.text().equals("致：")).allMatch(b->b.fields().size()==1&&b.fields().get(0).start()==2&&b.fields().get(0).end()==2));
        for(var b:blocks)for(var f:b.fields()){
            assertTrue(f.end()>f.start()||b.text().isEmpty()||!TemplateFields.captionLabel(b.text()).isEmpty());
            assertEquals(f.original(),b.text().substring(f.start(),f.end()));
        }
        assertTrue(blocks.stream().flatMap(b->b.fields().stream()).anyMatch(f->f.kind().equals("money")));
        assertEquals(TemplateLayout.inspect(source).stream().map(TemplateLayout.Block::text).toList(),blocks.stream().map(TemplateLayout.Block::text).toList());
        java.nio.file.Path evidence=java.nio.file.Path.of("artifacts/20261023/原模板字段识别");java.nio.file.Files.createDirectories(evidence);
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(evidence.resolve("original-letter-fields.json").toFile(),blocks);
        var editable=blocks.stream().map(b->new TemplateLayout.Block(b.id(),b.text(),b.fields().stream().anyMatch(TemplateFields.Field::enabled),b.fields())).toList();
        var logical=TemplateFields.logical(editable);String factors="个人贷款合同。合同编号：TEST-ORIGINAL-01。委托人及发函方：测试银行支行。收件人：陈景安。承办律师姓名：赵衡。律师联系电话：000-00000999。首期逾期起算日：2026年10月2日。核算截止日：2026年10月22日。拟定发函日期：2026年10月22日。已到期逾期本金合计5000元。未付正常利息合计1650元。本息合计6650元。";
        String r="---起草要素---\n"+json.writeValueAsString(new DraftingFacts.Factors("甲乙",factors,"催告已到期款项",""))+request(logical);
        java.nio.file.Files.writeString(evidence.resolve("logical-debug.json"),json.writeValueAsString(logical));
        var result=TemplateDrafting.read(TemplateDrafting.finish(json.writeValueAsString(new TemplateDrafting.Document("version",logical)),r));
        byte[] filled=TemplateLayout.fill(source,result.blocks(),editable);
        java.nio.file.Files.write(evidence.resolve("原始模板_程序回填核验.docx"),filled);
        assertTrue(TemplateLayout.inspect(filled).stream().anyMatch(b->b.text().contains("5000.00元")));
        assertArrayEquals(source,java.nio.file.Files.readAllBytes(file));
    }
    @Test void fieldOnlyResponseCannotRewriteFixedTextOrInventValues()throws Exception{
        var f=new TemplateFields.Field("p0-2-6","p0",2,6,"____","收件人","text","","underline",true);
        var original=new TemplateLayout.Block("p0","致：____，请履行。",true,List.of(f));
        var logical=TemplateFields.logical(List.of(original));
        String r="---起草要素---\n"+json.writeValueAsString(new DraftingFacts.Factors("收件人：张某","张某为借款人。","催告",""))+request(logical);
        String raw="{\"documentTemplate\":\"version\",\"fieldValues\":[{\"fieldId\":\"p0-2-6\",\"value\":\"张某\",\"quote\":\"张某为借款人\"}]}";
        assertEquals("致：张某，请履行。",TemplateDrafting.read(TemplateDrafting.finish(raw,r)).blocks().get(0).text());
        assertThrows(DraftingException.class,()->TemplateDrafting.finish(raw.replace("\\\"value\\\":\\\"张某\\\"","x").replace("张某","李某"),r));
        assertThrows(DraftingException.class,()->TemplateDrafting.finish(raw.replace("p0-2-6","p0-0-9"),r));
    }
    private final ObjectMapper json=new ObjectMapper();
    private String request(List<TemplateLayout.Block> blocks)throws Exception{return "---版式填写JSON开始---\n"+json.writeValueAsString(new TemplateDrafting.Document("version",blocks))+"\n---版式填写JSON结束---";}
    @Test void labelledLoanAmountsAndDeadlineSurviveOmittedModelPlans()throws Exception{
        String facts="已到期逾期本金合计5000.00元。未付正常利息合计1650.00元。本息合计6650.00元。逾期罚息合计20.95元。本次催收总额6670.95元。催告期限：自发函当日签收后次日起算7个自然日，截止2026年10月29日24时。";
        var blocks=List.of(new TemplateLayout.Block("p0","本息合计{{本息合计}}元，逾期罚息{{罚息}}元，本次催收总额{{本次催收总额}}元。请于{{催告届满日}}付款。",true));
        String r="---起草要素---\n"+json.writeValueAsString(new DraftingFacts.Factors("甲乙",facts,"催告到期款项",""))+"\n"+request(blocks);
        var result=TemplateDrafting.read(TemplateDrafting.finish(json.writeValueAsString(new TemplateDrafting.Document("version",blocks)),r));
        assertEquals("本息合计6650.00元，逾期罚息20.95元，本次催收总额6670.95元。请于2026年10月29日付款。",result.blocks().get(0).text());
        assertEquals(4,result.calculationResults().size());assertTrue(result.calculationResults().stream().allMatch(c->"calculated".equals(c.status())&&!c.sources().isEmpty()));
        assertTrue(result.calculations().stream().anyMatch(c->"本次催收总额".equals(c.label())&&"SUM".equals(c.operation())));
        assertDoesNotThrow(()->TemplateDrafting.finish(json.writeValueAsString(result),r,true));
        String wrappedDate="---起草要素---\n"+json.writeValueAsString(new DraftingFacts.Factors("甲乙",facts.replace("截止2026年10月29日","截止2026年10月29\n日"),"催告到期款项","催告届满日：2026年10月29日。"))+"\n"+request(blocks);
        var wrapped=TemplateDrafting.read(TemplateDrafting.finish(json.writeValueAsString(new TemplateDrafting.Document("version",blocks)),wrappedDate));
        assertTrue(wrapped.calculationResults().stream().allMatch(c->"calculated".equals(c.status())));
        String inconsistent=r.replace("本次催收总额6670.95元","本次催收总额9999.00元");
        var mismatch=TemplateDrafting.read(TemplateDrafting.finish(json.writeValueAsString(new TemplateDrafting.Document("version",blocks)),inconsistent));
        assertTrue(mismatch.calculationResults().stream().anyMatch(c->"本次催收总额".equals(c.label())&&"pending".equals(c.status())));
    }
    @Test void ambiguousPenaltyAndInvalidDeadlineCannotBecomeCalculated()throws Exception{
        String facts="已到期逾期本金合计5000元。未付正常利息合计1650元。逾期罚息合计20.95元。逾期罚息合计21.95元。催告期限：7个自然日，截止2026年2月30日24时。";
        var blocks=List.of(new TemplateLayout.Block("p0","罚息{{罚息}}元，本次催收总额{{本次催收总额}}元。截止{{催告届满日}}。",true));
        String r="---起草要素---\n"+json.writeValueAsString(new DraftingFacts.Factors("甲乙",facts,"催收",""))+"\n"+request(blocks);
        var result=TemplateDrafting.read(TemplateDrafting.finish(json.writeValueAsString(new TemplateDrafting.Document("version",blocks)),r));
        assertEquals(blocks.get(0).text(),result.blocks().get(0).text());
        assertTrue(result.calculationResults().stream().allMatch(c->"pending".equals(c.status())));
    }
    @Test void explicitTotalsStayDistinctFromIndividualOverdueInstallments()throws Exception{
        String facts="第一期未付正常利息100元；第二期未付正常利息200元。已到期逾期本金合计1000元，未付正常利息合计300元，本息合计1300元。";
        var blocks=List.of(new TemplateLayout.Block("p0","本金{{逾期本金}}元，利息{{正常利息}}元，本息合计{{本息合计}}元。",true));
        String r="---起草要素---\n"+json.writeValueAsString(new DraftingFacts.Factors("甲乙",facts,"催收已到期款项","催告期限7个自然日。"))+"\n"+request(blocks);
        var result=TemplateDrafting.read(TemplateDrafting.finish(json.writeValueAsString(new TemplateDrafting.Document("version",blocks)),r));
        assertEquals("本金1000.00元，利息300.00元，本息合计1300.00元。",result.blocks().get(0).text());
        assertTrue(result.calculationResults().stream().allMatch(c->"calculated".equals(c.status())));
        String conflicting=r.replace("未付正常利息合计300元","未付正常利息合计300元，未付正常利息合计301元");
        assertFalse(TemplateSourceValues.read(conflicting).containsKey("interest"));
    }
    @Test void accountingCutoffDoesNotConflictWithPaymentDeadline()throws Exception{
        String facts="个人贷款合同。核算截止日：2026年10月22日。截止2026年10月29日支付到期款项。";
        var blocks=List.of(new TemplateLayout.Block("p0","核算截止日：{{核算截止日}}；截止 年 月 日。",true));
        String r="---起草要素---\n"+json.writeValueAsString(new DraftingFacts.Factors("甲乙",facts,"催收到期债务","催告期限7个自然日，截止2026年10月29日"))+"\n"+request(blocks);
        var result=TemplateDrafting.read(TemplateDrafting.finish(json.writeValueAsString(new TemplateDrafting.Document("version",blocks)),r));
        assertEquals("核算截止日：2026年10月22日；截止2026年10月22日。",result.blocks().get(0).text());
        String conflict=r.replace("核算截止日：2026年10月22日。","核算截止日：2026年10月22日。核对截止日：2026年10月23日。");
        assertFalse(TemplateSourceValues.read(conflict).containsKey("cutoffDate"));
    }
    @Test void fixedTemplateDeadlineCannotOverrideExplicitLetterDeadline()throws Exception{
        var blocks=List.of(new TemplateLayout.Block("p0","收到本函之日起三日内归还已到期款项。",true));
        String r="---起草要素---\n"+json.writeValueAsString(new DraftingFacts.Factors("甲乙","个人贷款合同。","催收已到期款项","本次催告履行期限：签收次日起7个自然日。"))+"\n"+request(blocks);
        assertTrue(assertThrows(DraftingException.class,()->TemplateDrafting.modelInput(r)).getMessage().contains("催告期限"));
        assertThrows(DraftingException.class,()->TemplateDrafting.finish(json.writeValueAsString(new TemplateDrafting.Document("version",blocks)),r));
        String matching=r.replace("7个自然日","3个自然日");
        assertDoesNotThrow(()->TemplateDrafting.modelInput(matching));
        assertThrows(DraftingException.class,()->TemplateDrafting.modelInput(r.replace("7个自然日","3个工作日")));
        assertDoesNotThrow(()->TemplateDrafting.modelInput(matching.replace("签收次日起3个自然日","自2026年10月22日签收后的次日起算3个自然日")));
        assertDoesNotThrow(()->TemplateDrafting.modelInput(matching.replace("三日内","十一日内").replace("3个自然日","11个自然日")));
        assertDoesNotThrow(()->TemplateDrafting.modelInput(matching.replace("三日内","三十七日内").replace("3个自然日","37个自然日")));
        var variable=List.of(new TemplateLayout.Block("p0","请于{{催告届满日}}前清偿。",true));
        String flexible="---起草要素---\n"+json.writeValueAsString(new DraftingFacts.Factors("甲乙","个人贷款合同。","催收到期款项","催告期限7个自然日。"))+"\n"+request(variable);
        assertDoesNotThrow(()->TemplateDrafting.modelInput(flexible));
    }
    @Test void selectedParagraphsCannotBeRewrittenAndMissingFieldsRemainOriginal()throws Exception{
        var source=List.of(new TemplateLayout.Block("p0","律师函",true),new TemplateLayout.Block("p1","收到本函三日内还款。",true),new TemplateLayout.Block("p2","电话：   ",true),new TemplateLayout.Block("p3","2026年  月  日",true),new TemplateLayout.Block("p4","致：{{收件人}}",true));
        String request=request(source),raw=json.writeValueAsString(new TemplateDrafting.Document("version",source));
        assertTrue(TemplateDrafting.modelInput(request).startsWith(request));
        assertTrue(TemplateDrafting.modelInput(request).contains("calculations"));
        assertEquals(source,TemplateDrafting.read(TemplateDrafting.finish(raw,request)).blocks());
        assertEquals(raw,DocumentWord.normalize(raw));
        for(String changed:List.of("收到本函七日内支付租金。","收到本函三日内还款。附加说明"))assertThrows(DraftingException.class,()->TemplateLayout.validateReplacement(source.get(1).text(),changed));
        for(String changed:List.of("电话：【联系电话待补充】","电话：【承办律师姓名待补充】"))assertThrows(DraftingException.class,()->TemplateLayout.validateReplacement(source.get(2).text(),changed));
        assertThrows(DraftingException.class,()->TemplateLayout.validateReplacement(source.get(3).text(),"【发函日期待确认】"));
        assertThrows(DraftingException.class,()->TemplateLayout.validateReplacement(source.get(0).text(),"租金催收函"));
        assertDoesNotThrow(()->TemplateLayout.validateReplacement("致：{{收件人}}","致：乙公司"));
        assertDoesNotThrow(()->TemplateLayout.validateReplacement("接受  行委托，贷款本金  元。","接受测试银行委托，贷款本金100元。"));
    }
    @Test void leaseAndMortgageMismatchFailsWithoutChangingTemplate()throws Exception{
        String factors="---起草要素---\n"+json.writeValueAsString(new DraftingFacts.Factors("甲、乙","房屋租赁合同，租金30元","依据合同起草律师函","2027年届满"));
        var blocks=List.of(new TemplateLayout.Block("p0","您拖欠我行贷款本金  元，个人一手房按揭贷款。",true));
        String request=factors+"\n"+request(blocks);
        assertThrows(DraftingException.class,()->TemplateDrafting.modelInput(request));
        assertThrows(DraftingException.class,()->TemplateDrafting.finish(json.writeValueAsString(new TemplateDrafting.Document("version",blocks)),request));
        assertEquals("您拖欠我行贷款本金  元，个人一手房按揭贷款。",blocks.get(0).text());
    }
    @Test void explicitAppendixFieldsFillEvenWhenTheModelLeavesBlanks()throws Exception{
        String facts="""
            个人住房按揭贷款合同
            合同编号 TEST MORTGAGE 2024 0601 001
            贷款总额600000元，实际未偿贷款本金537500元，合同签约日2024年6月1日。
            银行电话：000-00000100。借款人电话：000-00000200。
            附件一 履行情况确认表
            首期逾期起算日为2026年8月2日。核对截止日为2026年10月20日。
            三期已到期逾期本金合计7,500.00元，未付正常利息合计4,837.50元，本息合计12,337.50元。
            委托人及发函方：测试银行庐山支行。收件人：陈景安。
            模拟承办律师姓名：林清和；联系电话：000-00000300。
            拟定发函日期为2026年10月21日。
            """;
        var blocks=List.of(new TemplateLayout.Block("p0","致：",true),new TemplateLayout.Block("p1","接受  行委托，指派专职律师  。",true),
            new TemplateLayout.Block("p2","合同编号为  。贷款自 年 月 日开始逾期，截止 年 月 日，拖欠贷款本金 元，利息 元，本息合计 元。",true),
            new TemplateLayout.Block("p3","  律师 ",true),new TemplateLayout.Block("p4","电话：   ",true),new TemplateLayout.Block("p5","2026年  月  日 ",true),
            new TemplateLayout.Block("p6","账号：{{收款账号}}",true),new TemplateLayout.Block("p7","已到期本金  元",false));
        String request="---起草要素---\n"+json.writeValueAsString(new DraftingFacts.Factors("甲乙双方",facts,"催告到期本息","2044年6月1日合同到期"))+"\n"+request(blocks);
        var result=TemplateDrafting.read(TemplateDrafting.finish(json.writeValueAsString(new TemplateDrafting.Document("version",blocks)),request));
        assertEquals("致：陈景安",result.blocks().get(0).text());
        assertEquals("接受测试银行庐山支行委托，指派专职律师林清和。",result.blocks().get(1).text());
        assertEquals("合同编号为TEST MORTGAGE 2024 0601 001。贷款自2026年8月2日开始逾期，截止2026年10月20日，拖欠贷款本金7500.00元，利息4837.50元，本息合计12337.50元。",result.blocks().get(2).text());
        assertEquals("林清和律师 ",result.blocks().get(3).text());assertEquals("电话：000-00000300",result.blocks().get(4).text());
        assertEquals("2026年10月21日 ",result.blocks().get(5).text());assertEquals(blocks.get(6),result.blocks().get(6));assertEquals(blocks.get(7),result.blocks().get(7));
        assertFalse(TemplateDrafting.plain(result).contains("待补充"));assertTrue(TemplateDrafting.modelInput(request).contains("7500.00"));
        try(var doc=new XWPFDocument();var out=new ByteArrayOutputStream()) {
            for(var block:blocks)doc.createParagraph().createRun().setText(block.text());doc.write(out);
            byte[] filled=TemplateLayout.fill(out.toByteArray(),result.blocks());
            try(var after=new XWPFDocument(new ByteArrayInputStream(filled))){assertEquals(result.blocks().get(2).text(),after.getParagraphs().get(2).getText());}
        }
    }
    @Test void conflictingOrAbsentSourceLabelsAreNotGuessed() {
        String request="---起草要素---\n{\"parties\":\"甲乙\",\"facts\":\"贷款金额600000元。未指定收件人：张某。发函日期为2026年10月21日。发函日期为2026年10月22日。\",\"purpose\":\"催告\",\"deadline\":\"\"}";
        var known=TemplateSourceValues.read(request);assertFalse(known.containsKey("recipient"));assertFalse(known.containsKey("letterDate"));assertFalse(known.containsKey("principal"));
        assertEquals("贷款本金 元，发函日期：{{发函日期}}",TemplateSourceValues.fill("贷款本金 元，发函日期：{{发函日期}}","贷款本金 元，发函日期：{{发函日期}}","",known));
    }
    @Test void incompleteCalculationsCanBeSavedAndExportedAsDrafts()throws Exception{
        String id="a862da83-71c0-452d-b747-fd368b75571d";byte[] source;
        try(var doc=new XWPFDocument();var out=new ByteArrayOutputStream()){doc.createParagraph().createRun().setText("罚息{{罚息}}元");doc.write(out);source=out.toByteArray();}
        var blocks=TemplateLayout.inspect(source).stream().map(b->new TemplateLayout.Block(b.id(),b.text(),true)).toList();
        var version=new TemplateFiles.Version(id,1L,"模板.docx","docx",source,source,blocks);var conversion=org.mockito.Mockito.mock(DocumentConversion.class);
        org.mockito.Mockito.when(conversion.convert(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq("docx"),org.mockito.ArgumentMatchers.eq("pdf"))).thenReturn("%PDF-test".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var files=new TemplateFiles(null,conversion){@Override public Version load(String requested){return version;}};
        try{
            var exporter=new DocumentExport(files,new DocumentWord(),conversion);
            String r="---起草要素---\n"+json.writeValueAsString(new DraftingFacts.Factors("甲乙","贷款总额600000元，尚无罚息明细。","催告",""))+TemplateDrafting.prompt(version);
            String draft=TemplateDrafting.finish(json.writeValueAsString(new TemplateDrafting.Document(id,blocks)),r);
            assertDoesNotThrow(()->exporter.checkedDraft(draft,r));
            for(String format:List.of("docx","pdf","md","txt"))for(String layout:List.of("template","standard"))
                assertTrue(exporter.generate("律师函",draft,format,r,layout).bytes().length>0);
        }finally{conversion.close();}
    }
    @Test void wordFillsOnlyOriginalBlanksAndPreservesRunProperties()throws Exception{
        try(var doc=new XWPFDocument();var out=new ByteArrayOutputStream()){
            var p=doc.createParagraph();p.createRun().setText("本金");var slot=p.createRun();slot.setFontFamily("仿宋");slot.setFontSize(14);slot.setText("  ");p.createRun().setText("元，应当还款。");
            doc.createParagraph().createRun().setText("电话：");doc.createParagraph().createRun().setText("签名：{{律师}}");doc.write(out);byte[] source=out.toByteArray();
            byte[] filled=TemplateLayout.fill(source,List.of(new TemplateLayout.Block("p0","本金100元，应当还款。",true),new TemplateLayout.Block("p1","电话：12345",true),new TemplateLayout.Block("p2","签名：{{律师}}",true)));
            try(var after=new XWPFDocument(new ByteArrayInputStream(filled))){assertEquals("本金100元，应当还款。",after.getParagraphs().get(0).getText());assertEquals("仿宋",after.getParagraphs().get(0).getRuns().get(1).getFontFamily());assertEquals("电话：12345",after.getParagraphs().get(1).getText());assertEquals("签名：{{律师}}",after.getParagraphs().get(2).getText());}
            assertThrows(DraftingException.class,()->TemplateLayout.fill(source,List.of(new TemplateLayout.Block("p0","本金100元，不必还款。",true))));
        }
    }
    @Test void saveAndAllExportsValidateAgainstOriginalEvenForStandardLayout()throws Exception{
        String id="a862da83-71c0-452d-b747-fd368b75571d";byte[] source;
        try(var doc=new XWPFDocument();var out=new ByteArrayOutputStream()){doc.createParagraph().createRun().setText("收到本函三日内还款。");doc.createParagraph().createRun().setText("收件人：{{收件人}}");doc.write(out);source=out.toByteArray();}
        var blocks=TemplateLayout.inspect(source,true).stream().map(b->new TemplateLayout.Block(b.id(),b.text(),true,b.fields())).toList();
        var version=new TemplateFiles.Version(id,1L,"模板.docx","docx",source,source,blocks);var conversion=new DocumentConversion();
        var files=new TemplateFiles(null,conversion){@Override public Version load(String requested){assertEquals(id,requested);return version;}};
        try{
            var exporter=new DocumentExport(files,new DocumentWord(),conversion);String request=TemplateDrafting.prompt(version);
            String unchanged=json.writeValueAsString(new TemplateDrafting.Document(id,blocks));
            assertDoesNotThrow(()->exporter.checkedDraft(unchanged,request));
            for(String format:List.of("docx","md","txt"))assertTrue(exporter.generate("律师函",unchanged,format,request).bytes().length>0);
            String completed=json.writeValueAsString(new TemplateDrafting.Document(id,List.of(new TemplateLayout.Block("p0",blocks.get(0).text(),true),new TemplateLayout.Block("p1","收件人：合成测试人",true))));
            for(String format:List.of("docx","md","txt"))assertTrue(exporter.generate("律师函",completed,format,request).bytes().length>0);
            String changed=json.writeValueAsString(new TemplateDrafting.Document(id,List.of(new TemplateLayout.Block("p0","改成租金催收函",true),blocks.get(1))));
            assertThrows(DraftingException.class,()->exporter.checkedDraft(changed,request));
            for(String format:List.of("docx","pdf","md","txt"))for(String layout:List.of("template","standard"))assertThrows(DraftingException.class,()->exporter.generate("律师函",changed,format,request,layout));
        }finally{conversion.close();}
    }
}
