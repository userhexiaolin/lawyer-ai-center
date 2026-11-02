package org.ruoyi.service.lawyer;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.ruoyi.domain.entity.lawyer.*;
import org.ruoyi.common.core.exception.ServiceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("dev") @EnabledIfSystemProperty(named="dossier.mysql",matches="true")
class BankStatementMysqlTest {
    DossierMaterialsMysqlTest fixture;BankStatementService bank;BankStatementExport exports;
    static Path evidence;
    @BeforeAll static void schema()throws Exception{
        String requestedEvidence=System.getProperty("dossier.evidence");
        if(requestedEvidence==null)System.setProperty("dossier.evidence","docs/product/o001/evidence/task-86");
        try{DossierMaterialsMysqlTest.schema();}finally{if(requestedEvidence==null)System.clearProperty("dossier.evidence");}
        evidence=DossierMaterialsMysqlTest.evidence;
        var db=new JdbcTemplate(new DriverManagerDataSource(DossierMaterialsMysqlTest.url,DossierMaterialsMysqlTest.user,DossierMaterialsMysqlTest.password));
        String migration=Files.readString(DossierMaterialsMysqlTest.workspace.resolve("ruoyi-ai/docs/script/sql/update/2026-10-21-bank-review.sql")).replaceAll("(?m)--.*$","");for(String sql:migration.split(";"))if(!sql.isBlank())db.execute(sql);
    }
    @BeforeEach void setup()throws Exception{
        fixture=new DossierMaterialsMysqlTest();fixture.setup();for(String t:List.of("lawyer_case_bank_export","lawyer_case_bank_duplicate","lawyer_case_bank_change","lawyer_case_bank_row","lawyer_case_bank_page","lawyer_case_bank_run"))fixture.jdbc.update("DELETE FROM "+t);
        bank=new BankStatementService(fixture.jdbc,fixture.tx,fixture.materials,fixture.access);exports=new BankStatementExport(bank,fixture.materials,fixture.access,fixture.jdbc,fixture.tx,fixture.oss);fixture.worker.handlers(List.of(bank,exports));
    }
    @AfterEach void close(){fixture.close();}
    static BankStatementService.Filter filter(){return new BankStatementService.Filter("","","","","","","","",1,100);}
    void execute(long id){String lease=UUID.randomUUID().toString();assertTrue(fixture.worker.claim(id,lease));fixture.worker.run(id,lease);}
    CaseFileVersion csv(String name,String rows)throws Exception{var v=fixture.upload(name,(BankStatementParserTest.HEADER+rows).replace('\t',',').getBytes(StandardCharsets.UTF_8),null,true);fixture.execute(v);extract(v);return v;}
    void extract(CaseFileVersion v){bank.classify(11L,v.getMaterialId(),"bank");var job=bank.extract(11L,v.getId(),UUID.randomUUID().toString());execute(job.getId());assertTrue(Set.of("success","partial").contains(fixture.materials.job(11L,job.getId()).getState()),()->fixture.materials.job(11L,job.getId()).getErrorMessage());}
    BankStatementService.Edit edit(CaseBankRow r,String amount,String state){return new BankStatementService.Edit(r.getVersion(),Objects.toString(r.getTradeTimeRaw(),""),amount,r.getDirection(),r.getCurrency(),r.getBalanceRaw(),null,null,r.getTransactionRef(),r.getSummary(),state,true,"合成样本逐项查看原件后核对");}
    @Test void pendingIsExcludedCorrectionPersistsAndRerunKeepsHumanValueStale()throws Exception{
        var v=csv("normal.csv","2025-03-01\texpense\t50000.00\t52000.00\tCNY\tSYN-A02\tA001\tPartyB\n");var row=bank.list(11L,filter()).rows().get(0);assertEquals("0.00",bank.list(11L,filter()).totals().expense());
        var corrected=bank.edit(11L,row.getId(),edit(row,"50000.10","confirmed"));assertEquals("50000.10",bank.list(11L,filter()).totals().expense());assertEquals(1,bank.history(11L,row.getId()).size());
        assertEquals(409,assertThrows(ServiceException.class,()->bank.edit(11L,row.getId(),edit(row,"1.00","confirmed"))).getCode());
        extract(v);var saved=bank.detail(11L,row.getId());assertEquals("50000.10",saved.getAmount().toPlainString());assertEquals("stale",saved.getReviewStatus());assertNotNull(saved.getSuggestionJson());assertEquals(1,bank.list(11L,filter()).total());assertEquals("0.00",bank.list(11L,filter()).totals().expense());assertTrue(bank.history(11L,corrected.getId()).size()>=2);
    }
    @Test void crossFileDuplicatesArePreservedAndHumanDecisionControlsTotals()throws Exception{
        String line="2025-03-01\texpense\t50000.00\t52000.00\tCNY\tSYN-A02\tA001\tPartyB\n";csv("first.csv",line);csv("second.csv",line);
        var rows=bank.list(11L,filter()).rows();assertEquals(2,rows.size());for(var r:rows)bank.edit(11L,r.getId(),edit(r,r.getAmountRaw(),"confirmed"));
        var pairs=bank.duplicates(11L);assertEquals(1,pairs.size());assertEquals("0.00",bank.list(11L,filter()).totals().expense());var pair=pairs.get(0);
        bank.decide(11L,Long.valueOf(pair.get("id").toString()),new BankStatementService.Duplicate(((Number)pair.get("version")).intValue(),"same","同一交易号且原件金额相符"));assertEquals("50000.00",bank.list(11L,filter()).totals().expense());assertEquals(2,bank.list(11L,filter()).total());
        pair=bank.duplicates(11L).get(0);bank.decide(11L,Long.valueOf(pair.get("id").toString()),new BankStatementService.Duplicate(((Number)pair.get("version")).intValue(),"different","合成核对后确认是独立两笔"));assertEquals("100000.00",bank.list(11L,filter()).totals().expense());
    }
    @Test void sourceTamperingReplacementAndForeignTenantCannotConfirmOrRead()throws Exception{
        var v=csv("source.csv","2025-03-01\tincome\t2000.00\t102000.00\tCNY\tSYN-A01\tA001\tPartyB\n");var r=bank.list(11L,filter()).rows().get(0);var source=bank.source(11L,r.getId());assertTrue(source.verified());assertTrue(source.current());
        fixture.actor=202;assertThrows(ServiceException.class,()->bank.source(11L,r.getId()));assertThrows(ServiceException.class,()->bank.history(11L,r.getId()));assertThrows(ServiceException.class,()->exports.status(11L,1L));fixture.actor=101;fixture.tenant="111111";assertThrows(ServiceException.class,()->bank.list(11L,filter()));fixture.tenant="000000";
        bank.edit(11L,r.getId(),edit(r,r.getAmountRaw(),"confirmed"));fixture.jdbc.update("UPDATE lawyer_case_file_page SET text_content=CONCAT(text_content,'tampered') WHERE id=?",r.getPageId());assertFalse(bank.source(11L,r.getId()).verified());var current=bank.detail(11L,r.getId());assertThrows(ServiceException.class,()->bank.edit(11L,current.getId(),edit(current,current.getAmountRaw(),"confirmed")));
        fixture.upload("replacement.pdf",DossierFileReaderTest.pdf(1,false),v.getMaterialId(),false);assertEquals("stale",bank.list(11L,filter()).rows().get(0).getReviewStatus());
    }
    @Test void asynchronousExportFreezesAllSelectedRowsAndHasFourSheets()throws Exception{
        csv("export.csv","2025-03-01\tincome\t0.10\t0.10\tCNY\tSYN-A01\tA001\tPartyB\n2025-03-02\texpense\t0.01\t0.09\tCNY\tSYN-A02\tA001\tPartyB\n");for(var r:bank.list(11L,filter()).rows())bank.edit(11L,r.getId(),edit(r,r.getAmountRaw(),"confirmed"));
        String key=UUID.randomUUID().toString();var request=new BankStatementExport.Request(filter(),false,false,"xlsx",key);var receipt=exports.request(11L,request);assertEquals(receipt.id(),exports.request(11L,request).id());assertEquals(receipt.id(),exports.list(11L).get(0).get("id"));
        var r=bank.list(11L,filter()).rows().get(0);bank.edit(11L,r.getId(),edit(r,"999.00","pending_review"));execute(Long.parseLong(receipt.jobId()));assertEquals("success",exports.status(11L,Long.valueOf(receipt.id())).get("state"));
        byte[] bytes=exports.download(11L,Long.valueOf(receipt.id()));try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){assertEquals(4,book.getNumberOfSheets());assertEquals(r.getFileVersionId().toString(),book.getSheet("来源").getRow(1).getCell(2).getStringCellValue());assertTrue(book.getSheet("来源").getRow(1).getCell(3).getStringCellValue().matches("[a-f0-9]{64}"));assertEquals(2,book.getSheetAt(0).getLastRowNum());assertEquals("0.10",book.getSheetAt(0).getRow(1).getCell(5).getStringCellValue());assertTrue(book.getSheet("导出说明").getRow(6).getCell(1).getStringCellValue().contains("0.09"));}
        Files.write(evidence.resolve("bank-snapshot.xlsx"),bytes);fixture.actor=202;assertThrows(ServiceException.class,()->exports.download(11L,Long.valueOf(receipt.id())));
    }
    @Test void filteredExportKeepsOnlyRelatedSourcesAndCoverage()throws Exception{
        var selected=csv("selected.csv","2025-03-01\tincome\t10.00\t10.00\tCNY\tSYN-SELECTED\tA001\tPartyA\n");
        String unrelated="2025-03-02\tincome\t20.00\t20.00\tCNY\tSYN-UNRELATED\tB001\tPartyB\n";
        csv("unrelated-one.csv",unrelated);csv("unrelated-two.csv",unrelated);
        assertFalse(bank.duplicates(11L).isEmpty(),"The fixture must contain an unrelated duplicate group");
        var scope=new BankStatementService.Filter(selected.getId().toString(),"","","","","","","",1,1);
        var receipt=exports.request(11L,new BankStatementExport.Request(scope,true,false,"xlsx",UUID.randomUUID().toString()));
        var snapshot=new ObjectMapper().readTree(fixture.jdbc.queryForObject("SELECT snapshot_json FROM lawyer_case_bank_export WHERE id=?",String.class,receipt.id()));
        assertEquals(1,snapshot.get("rows").size());assertEquals(1,snapshot.get("sources").size());
        assertEquals(1,snapshot.get("unparsed").size(),"Coverage must stay within exported source versions");
        assertEquals(selected.getId().toString(),snapshot.get("unparsed").get(0).get("fileVersionId").asText());
        assertEquals(0,snapshot.get("duplicates").size(),"Unrelated duplicate groups must not enter this export");
        execute(Long.parseLong(receipt.jobId()));byte[] bytes=exports.download(11L,Long.valueOf(receipt.id()));
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){
            assertEquals(4,book.getNumberOfSheets());assertEquals(1,book.getSheet("明细").getLastRowNum());
            assertEquals(selected.getId().toString(),book.getSheet("来源").getRow(1).getCell(2).getStringCellValue());
            boolean coverageFound=false;
            for(var row:book.getSheet("异常")){
                for(var cell:row)assertFalse(cell.toString().contains("SYN-UNRELATED"));
                if(row.getRowNum()>0&&"unparsed".equals(row.getCell(1).getStringCellValue())){coverageFound=true;var page=new ObjectMapper().readTree(row.getCell(2).getStringCellValue());assertEquals(selected.getId().toString(),page.get("fileVersionId").asText());}
            }
            assertTrue(coverageFound,"The actual workbook must retain its page-level source coordinates");
        }
        Files.write(evidence.resolve("bank-scoped-snapshot.xlsx"),bytes);
    }
    @Test void deduplicatedPendingExportKeepsTheConfirmedRepresentative()throws Exception{
        String line="2025-03-01\tincome\t10.00\t10.00\tCNY\tSYN-SAME\tA001\tPartyA\n";
        var first=csv("pending-first.csv",line);var second=csv("confirmed-second.csv",line);
        var confirmed=bank.list(11L,filter()).rows().stream().filter(r->r.getFileVersionId().equals(second.getId())).findFirst().orElseThrow();
        bank.edit(11L,confirmed.getId(),edit(confirmed,"10.00","confirmed"));
        var pair=bank.duplicates(11L).get(0);
        bank.decide(11L,Long.valueOf(pair.get("id").toString()),new BankStatementService.Duplicate(((Number)pair.get("version")).intValue(),"same","合成原件同一交易，保留两个来源"));
        var receipt=exports.request(11L,new BankStatementExport.Request(filter(),true,true,"xlsx",UUID.randomUUID().toString()));
        var snapshot=new ObjectMapper().readTree(fixture.jdbc.queryForObject("SELECT snapshot_json FROM lawyer_case_bank_export WHERE id=?",String.class,receipt.id()));
        assertEquals(1,snapshot.get("rows").size());assertEquals(confirmed.getId().toString(),snapshot.get("rows").get(0).get("id").asText());
        assertEquals("confirmed",snapshot.get("rows").get(0).get("reviewStatus").asText());
        assertEquals("10.00",snapshot.get("totals").get("income").asText());assertEquals(2,snapshot.get("sources").size());
        assertEquals("pending_review",snapshot.get("sources").get(0).get("reviewStatus").asText());
        assertEquals(first.getId().toString(),snapshot.get("sources").get(0).get("fileVersionId").asText());
        execute(Long.parseLong(receipt.jobId()));byte[] bytes=exports.download(11L,Long.valueOf(receipt.id()));
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){
            assertEquals(confirmed.getId().toString(),book.getSheet("明细").getRow(1).getCell(0).getStringCellValue());
            assertEquals(2,book.getSheet("来源").getLastRowNum());
            assertEquals("pending_review",book.getSheet("来源").getRow(1).getCell(12).getStringCellValue());
        }
        Files.write(evidence.resolve("bank-deduplicated-snapshot.xlsx"),bytes);
    }
    @Test void manualUnknownPageCanOnlyBeSavedPendingAndNeverConfirmed()throws Exception{
        var v=fixture.upload("manual.pdf",DossierFileReaderTest.pdf(3,true),null,false);fixture.worker.close();var reader=new DossierFileReader(image->"",org.mockito.Mockito.mock(org.ruoyi.service.legal.DocumentConversion.class));fixture.materials=fixture.service(reader);fixture.worker=new DossierParseWorker(fixture.jdbc,fixture.tx,fixture.materials,reader);bank=new BankStatementService(fixture.jdbc,fixture.tx,fixture.materials,fixture.access);fixture.worker.handlers(List.of(bank));fixture.execute(v);
        var e=new BankStatementService.Edit(1,"2025-03-01","50.00","expense","CNY","10.00",null,null,"SYN-MANUAL","人工暂存","pending_review",true,"不可读页合成人工暂存");var row=bank.manual(11L,new BankStatementService.Manual(v.getId(),3,0,1,e));assertEquals("pending_review",row.getReviewStatus());assertFalse(bank.source(11L,row.getId()).verified());assertThrows(ServiceException.class,()->bank.edit(11L,row.getId(),edit(row,"50.00","confirmed")));
    }
    @Test void goldenTextAndRealScanStatementsReportMeasuredMetricsAndAnomalies()throws Exception{
        var ocr=new LocalDossierOcr();org.springframework.test.util.ReflectionTestUtils.setField(ocr,"language","en-US");var reader=new DossierFileReader(ocr,mock(org.ruoyi.service.legal.DocumentConversion.class));fixture.worker.close();fixture.materials=fixture.service(reader);fixture.worker=new DossierParseWorker(fixture.jdbc,fixture.tx,fixture.materials,reader);bank=new BankStatementService(fixture.jdbc,fixture.tx,fixture.materials,fixture.access);fixture.worker.handlers(List.of(bank));
        var gold=new ObjectMapper().readTree(Files.readString(DossierMaterialsMysqlTest.workspace.resolve("docs/product/o001/fixtures/gold.json")));var expected=new ArrayList<Map<String,String>>();var versions=new HashMap<String,Long>();
        for(String file:List.of("B01","B02")){
            var pageRows=new ArrayList<List<String>>();for(int page=1;page<=2;page++){var lines=new ArrayList<String>();lines.add("Date Direction Amount Balance Currency Ref Account Counterparty");for(var r:gold.get("bankRows"))if(file.equals(r.get("file").asText())&&r.get("physicalPageIndex").asInt()==page){lines.add(String.join(" ",r.get("tradeDate").asText(),r.get("direction").asText(),r.get("amount").asText(),r.get("balance").asText(),r.get("currency").asText(),r.get("transactionRef").asText(),r.get("account").asText(),"PartyB"));expected.add(Map.of("file",file,"page",Integer.toString(page),"ref",r.get("transactionRef").asText(),"amount",r.get("amount").asText(),"balance",r.get("balance").asText(),"date",r.get("tradeDate").asText(),"direction",r.get("direction").asText(),"account",r.get("account").asText(),"line",r.get("tableRow").asText(),"currency",r.get("currency").asText()));}pageRows.add(lines);}
            byte[] bytes=pdf(pageRows,file.equals("B02"));Files.write(evidence.resolve(file+(file.equals("B02")?"-scan":"-text")+".pdf"),bytes);var v=fixture.upload(file+".pdf",bytes,null,false);fixture.execute(v);versions.put(file,v.getId());Files.writeString(evidence.resolve(file+"-actual-page-text.json"),BankStatementService.json(fixture.materials.pages(11L,v.getId()).stream().map(p->Map.of("page",p.getPageIndex(),"status",p.getExtractStatus(),"text",Objects.toString(p.getTextContent(),""),"error",Objects.toString(p.getErrorCode(),""))).toList()));extract(v);
        }
        var rows=bank.list(11L,filter()).rows();long matched=0,fields=0;for(var e:expected){var r=rows.stream().filter(x->x.getFileVersionId().equals(versions.get(e.get("file")))&&e.get("ref").equals(x.getTransactionRef())).findFirst();if(r.isEmpty())continue;matched++;var value=r.get();if(e.get("amount").equals(value.getAmount()==null?null:value.getAmount().toPlainString()))fields++;if(e.get("balance").equals(value.getBalance()==null?null:value.getBalance().toPlainString()))fields++;if(e.get("date").equals(value.getTradeAt()))fields++;if(e.get("direction").equals(value.getDirection()))fields++;if(e.get("account").equals(value.getAccountRaw()))fields++;}
        double recall=(double)matched/expected.size(),precision=rows.isEmpty()?0:(double)matched/rows.size(),accuracy=(double)fields/(expected.size()*5);var metrics=new LinkedHashMap<String,Object>();metrics.put("syntheticOnly",true);metrics.put("expectedRows",18);metrics.put("actualRows",rows.size());metrics.put("matchedReferences",matched);metrics.put("rowRecall",recall);metrics.put("rowPrecision",precision);metrics.put("keyFieldAccuracy",accuracy);metrics.put("scanOcr","real local Windows OCR / en-US (English source)");metrics.put("confirmedIncome",bank.list(11L,filter()).totals().income());metrics.put("confirmedExpense",bank.list(11L,filter()).totals().expense());Files.writeString(evidence.resolve("bank-gold-metrics.json"),BankStatementService.json(metrics));
        // Raw OCR metrics are measured, not assumed perfect. Release safety also requires
        // complete human correction, real sources, immutable audit and exact final totals.
        assertEquals(10,rows.stream().filter(r->r.getFileVersionId().equals(versions.get("B01"))).count());
        assertTrue(recall>=0&&recall<=1);assertTrue(precision>=0&&precision<=1);assertTrue(accuracy>=0&&accuracy<=1);
        assertTrue(rows.stream().noneMatch(r->"confirmed".equals(r.getReviewStatus())));assertEquals("0.00",bank.list(11L,filter()).totals().expense());
        if(recall<1||accuracy<1)assertTrue(bank.runs(11L).stream().anyMatch(r->!"success".equals(r.get("state"))));
        for(var raw:rows)assertTrue(bank.source(11L,raw.getId()).verified());
        var correctedIds=new HashSet<Long>();
        for(var e:expected){
            long version=versions.get(e.get("file"));int physical=Integer.parseInt(e.get("page"));
            var page=fixture.materials.pages(11L,version).stream().filter(value->value.getPageIndex()==physical).findFirst().orElseThrow();
            String text=page.getTextContent();int reference=text.indexOf(e.get("ref")),start,end;
            if(reference>=0){start=text.lastIndexOf('\n',reference)+1;end=text.indexOf('\n',reference);if(end<0)end=text.length();}
            else{String[] lines=text.split("\n",-1);int line=Integer.parseInt(e.get("line"));assertTrue(line<lines.length,"A human source must still be present on its physical page");start=0;for(int i=0;i<line;i++)start+=lines[i].length()+1;end=start+lines[line].length();}
            final int at=start;
            var existing=bank.list(11L,filter()).rows().stream().filter(value->value.getFileVersionId()==version&&value.getPageIndex()==physical&&value.getFragmentStart()==at).findFirst();
            var values=new BankStatementService.Edit(existing.map(CaseBankRow::getVersion).orElse(1),e.get("date"),e.get("amount"),e.get("direction"),e.get("currency"),e.get("balance"),null,"PartyB",e.get("ref"),"SYNTHETIC human review", "confirmed",true,"测试模拟人工逐页对照金标，纠正OCR；余额异常仍保留",e.get("account"));
            CaseBankRow corrected;
            if(existing.isPresent())corrected=bank.edit(11L,existing.get().getId(),values);
            else{var draft=bank.manual(11L,new BankStatementService.Manual(version,physical,start,end,values));corrected=bank.edit(11L,draft.getId(),new BankStatementService.Edit(draft.getVersion(),e.get("date"),e.get("amount"),e.get("direction"),e.get("currency"),e.get("balance"),null,"PartyB",e.get("ref"),"SYNTHETIC human review","confirmed",true,"测试模拟人工核对补录行原件",e.get("account")));}
            correctedIds.add(corrected.getId());assertTrue(bank.source(11L,corrected.getId()).verified());assertEquals(e.get("amount"),corrected.getAmount().toPlainString());assertEquals(e.get("balance"),corrected.getBalance().toPlainString());assertEquals(e.get("date"),corrected.getTradeAt());assertEquals(e.get("direction"),corrected.getDirection());assertEquals(e.get("account"),corrected.getAccountRaw());assertEquals(e.get("currency"),corrected.getCurrency());assertEquals(e.get("ref"),corrected.getTransactionRef());assertFalse(bank.history(11L,corrected.getId()).isEmpty());
        }
        assertEquals(18,correctedIds.size());assertEquals(18,bank.list(11L,filter()).rows().stream().filter(value->"confirmed".equals(value.getReviewStatus())).count());
        for(var pair:bank.duplicates(11L)){var left=bank.detail(11L,Long.valueOf(pair.get("leftRowId").toString()));var right=bank.detail(11L,Long.valueOf(pair.get("rightRowId").toString()));String decision=Objects.equals(left.getTransactionRef(),right.getTransactionRef())?"same":"different";bank.decide(11L,Long.valueOf(pair.get("id").toString()),new BankStatementService.Duplicate(((Number)pair.get("version")).intValue(),decision,"测试模拟人工对照两份原件交易号后判定"));}
        var totals=bank.list(11L,filter()).totals();assertEquals("16200.10",totals.income());assertEquals("103250.10",totals.expense());assertEquals("-87050.00",totals.net());
        assertTrue(bank.list(11L,filter()).rows().stream().anyMatch(r->r.getAnomalyJson().contains("BALANCE_MISMATCH")));
        metrics.put("manualVerifiedRows",correctedIds.size());metrics.put("manualIncome",totals.income());metrics.put("manualExpense",totals.expense());metrics.put("manualNet",totals.net());metrics.put("manualFieldAccuracy",1.0);metrics.put("manualStep","explicit test simulation through the same correction/confirmation/duplicate APIs; never production fixture lookup");Files.writeString(evidence.resolve("bank-gold-metrics.json"),BankStatementService.json(metrics));
    }
    @Test void ocrDigitShiftStaysOutOfTotalsUntilExplicitCorrection()throws Exception{
        byte[] bytes=pdf(List.of(List.of("Date Direction Amount Balance Currency Ref Account Counterparty","2025-03-01 expense 50000.00 52000.00 CNY SYN-B03 A001 PartyB")),true);
        var reader=new DossierFileReader(image->BankStatementParserTest.HEADER+"2025-03-01\texpense\t500000.00\t52000.00\tCNY\tSYN-B03\tA001\tPartyB\n",mock(org.ruoyi.service.legal.DocumentConversion.class));
        fixture.worker.close();fixture.materials=fixture.service(reader);fixture.worker=new DossierParseWorker(fixture.jdbc,fixture.tx,fixture.materials,reader);bank=new BankStatementService(fixture.jdbc,fixture.tx,fixture.materials,fixture.access);fixture.worker.handlers(List.of(bank));
        var v=fixture.upload("B03-ocr-shift.pdf",bytes,null,false);fixture.execute(v);extract(v);var row=bank.list(11L,filter()).rows().get(0);assertEquals("500000.00",row.getAmount().toPlainString());assertEquals("0.00",bank.list(11L,filter()).totals().expense());
        var corrected=bank.edit(11L,row.getId(),edit(row,"50000.00","confirmed"));assertEquals("50000.00",corrected.getAmount().toPlainString());assertEquals("50000.00",bank.list(11L,filter()).totals().expense());assertTrue(bank.history(11L,row.getId()).get(0).get("beforeJson").toString().contains("500000.00"));
        Files.write(evidence.resolve("B03-real-source-test-only-ocr-shift.pdf"),bytes);
    }
    @Test void currenciesAreNeverAddedAcrossUnits()throws Exception{
        csv("currencies.csv","2025-03-01\tincome\t10.00\t10.00\tUSD\tSYN-U01\tA001\tPartyB\n2025-03-02\tincome\t1.00\t1.00\tCNY\tSYN-C01\tA001\tPartyB\n");for(var r:bank.list(11L,filter()).rows())bank.edit(11L,r.getId(),edit(r,r.getAmountRaw(),"confirmed"));var totals=bank.list(11L,filter()).totals();assertEquals(2,totals.currencies().size());assertEquals("10.00",totals.currencies().get("USD").get("income"));assertEquals("1.00",totals.currencies().get("CNY").get("income"));assertFalse(totals.income().matches("[0-9.]+"));
    }
    @Test void splitKeepsOriginalHistoryAndCreatesOnlyPendingAnchoredPartsAtomically()throws Exception{
        var v=csv("merged.csv","2025-03-01\tincome\t10.00\t10.00\tCNY\tSYN-ONE\tA001\tPartyB\t2025-03-02\texpense\t4.00\t6.00\tCNY\tSYN-TWO\tA001\tPartyC\n");var original=bank.list(11L,filter()).rows().get(0);var source=bank.source(11L,original.getId());int middle=source.start()+source.fragment().indexOf("2025-03-02");
        var one=new BankStatementService.Edit(1,"2025-03-01","10.00","income","CNY","10.00",null,"PartyB","SYN-ONE","拆分第一笔","confirmed",true,"合成原页逐段拆分第一笔","A001");var two=new BankStatementService.Edit(1,"2025-03-02","4.00","expense","CNY","6.00",null,"PartyC","SYN-TWO","拆分第二笔","confirmed",true,"合成原页逐段拆分第二笔","A001");
        var a=new BankStatementService.Manual(v.getId(),1,source.start(),middle,one);var b=new BankStatementService.Manual(v.getId(),1,middle,source.end(),two);
        assertThrows(ServiceException.class,()->bank.split(11L,original.getId(),new BankStatementService.Split(original.getVersion(),List.of(a,new BankStatementService.Manual(v.getId()+1,1,middle,source.end(),two)),"错误跨版本必须整体回滚")));assertEquals(1,bank.list(11L,filter()).total());
        var children=bank.split(11L,original.getId(),new BankStatementService.Split(original.getVersion(),List.of(a,b),"合成原页两笔交易合并成一行，逐段拆分"));assertEquals(2,children.size());for(var row:children){assertEquals("pending_review",row.getReviewStatus());assertTrue(bank.source(11L,row.getId()).verified());assertTrue(row.getSourceVariant()>0);}
        assertEquals(3,bank.list(11L,filter()).total());assertFalse(bank.detail(11L,original.getId()).getIncluded());assertEquals("rejected",bank.detail(11L,original.getId()).getReviewStatus());assertTrue(bank.history(11L,original.getId()).stream().anyMatch(h->"split".equals(h.get("action"))));assertEquals("0.00",bank.list(11L,filter()).totals().income());
    }
    @Test void guardedMigrationChecksTargetBackupChecksumAndRestoresSyntheticBackup()throws Exception{
        Path root=DossierMaterialsMysqlTest.workspace,classes=Files.createTempDirectory("bank-migration-classes-");String javaExe=Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),javac=Path.of(System.getProperty("java.home"),"bin","javac.exe").toString();
        var compiler=new ProcessBuilder(javac,"-encoding","UTF-8","-d",classes.toString(),root.resolve("ruoyi-ai/scripts/deploy/DossierMigration.java").toString(),root.resolve("ruoyi-ai/scripts/deploy/DossierExtensionMigration.java").toString()).redirectErrorStream(true).redirectOutput(evidence.resolve("migration-tool-compile.log").toFile()).start();assertTrue(compiler.waitFor(45,java.util.concurrent.TimeUnit.SECONDS));assertEquals(0,compiler.exitValue());
        String db=DossierMaterialsMysqlTest.database;assertTrue(db.startsWith("codex_dossier85_"));var dump=new StringBuilder("-- SYNTHETIC ONLY\n");
        try(var c=java.sql.DriverManager.getConnection(DossierMaterialsMysqlTest.url,DossierMaterialsMysqlTest.user,DossierMaterialsMysqlTest.password);var statement=c.createStatement()){
            var tables=new ArrayList<String>();try(var r=c.getMetaData().getTables(db,null,"%",new String[]{"TABLE"})){while(r.next())tables.add(r.getString("TABLE_NAME"));}
            for(String table:tables){assertTrue(table.matches("[a-zA-Z0-9_]+"));try(var ddl=statement.executeQuery("SHOW CREATE TABLE `"+table+"`")){ddl.next();dump.append(ddl.getString(2)).append(";\n");}try(var data=statement.executeQuery("SELECT * FROM `"+table+"`")){while(data.next()){dump.append("INSERT INTO `").append(table).append("` VALUES(");for(int col=1;col<=data.getMetaData().getColumnCount();col++){if(col>1)dump.append(',');Object value=data.getObject(col);dump.append(value==null?"NULL":value instanceof Number?value.toString():"'"+value.toString().replace("\\","\\\\").replace("'","''")+"'");}dump.append(");\n");}}}
            String restore=db+"_restore";statement.execute("CREATE DATABASE `"+restore+"` CHARACTER SET utf8mb4");c.setCatalog(restore);try(var restored=c.createStatement()){for(String sql:dump.toString().replaceAll("(?m)--.*$","").split(";\\n"))if(!sql.isBlank())restored.execute(sql);try(var count=restored.executeQuery("SELECT COUNT(*) FROM lawyer_case")){count.next();assertEquals(3,count.getInt(1));}}
        }
        Path backup=evidence.resolve("migration-synthetic-backup.sql"),receipt=evidence.resolve("migration-synthetic-backup.properties");Files.writeString(backup,dump.toString());var props=new Properties();props.setProperty("database",db);props.setProperty("file",backup.toString());props.setProperty("sha256",DossierFiles.hash(Files.readAllBytes(backup)));try(var out=Files.newBufferedWriter(receipt)){props.store(out,"SYNTHETIC ONLY; restored to a new synthetic database");}
        String driver=Path.of(Class.forName("com.mysql.cj.jdbc.Driver").getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        var command=List.of(javaExe,"-cp",classes+File.pathSeparator+driver,"DossierExtensionMigration","migrate",root.toString(),"2026-10-21-bank-review.sql");var env=new HashMap<String,String>();env.put("DOSSIER_JDBC_URL",DossierMaterialsMysqlTest.url);env.put("DOSSIER_DB_USER",DossierMaterialsMysqlTest.user);env.put("DOSSIER_DB_PASSWORD",DossierMaterialsMysqlTest.password);env.put("DOSSIER_EXPECTED_DATABASE",db);env.put("DOSSIER_OPERATOR","bank-synthetic-rehearsal");env.put("DOSSIER_BACKUP_RECEIPT",receipt.toString());
        var wrong=new HashMap<>(env);wrong.put("DOSSIER_EXPECTED_DATABASE",db+"_wrong");assertNotEquals(0,invoke(command,wrong,"migration-target-refusal.log"));wrong=new HashMap<>(env);wrong.put("DOSSIER_BACKUP_RECEIPT",receipt+".absent");assertNotEquals(0,invoke(command,wrong,"migration-backup-refusal.log"));assertNotEquals(0,invoke(command,env,"migration-unregistered-refusal.log"));
        var resume=new ArrayList<>(command);resume.add("--resume");assertEquals(0,invoke(resume,env,"migration-resume.log"));assertEquals(0,invoke(command,env,"migration-repeat.log"));
        String checksum=DossierFiles.hash(Files.readAllBytes(root.resolve("ruoyi-ai/docs/script/sql/update/2026-10-21-bank-review.sql")));fixture.jdbc.update("UPDATE stage4_schema_migration SET sha256=REPEAT('0',64) WHERE version='2026-10-21-bank-review.sql'");try{assertNotEquals(0,invoke(command,env,"migration-checksum-refusal.log"));}finally{fixture.jdbc.update("UPDATE stage4_schema_migration SET sha256=? WHERE version='2026-10-21-bank-review.sql'",checksum);}
        fixture.jdbc.execute("ALTER TABLE lawyer_case_bank_row MODIFY amount DECIMAL(20,3) NULL");try{assertNotEquals(0,invoke(command,env,"migration-scale-refusal.log"));}finally{fixture.jdbc.execute("ALTER TABLE lawyer_case_bank_row MODIFY amount DECIMAL(20,2) NULL");}
        assertEquals(1,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM stage4_schema_migration WHERE version='2026-10-21-bank-review.sql'",Integer.class));assertEquals(3,fixture.jdbc.queryForObject("SELECT COUNT(*) FROM lawyer_case",Integer.class));
        Files.writeString(evidence.resolve("migration-rehearsal.json"),BankStatementService.json(Map.of("syntheticOnly",true,"database",db,"result","PASS","checks",List.of("backup_restored_to_separate_synthetic_database","target_mismatch_refused","missing_backup_refused","unregistered_schema_refused","explicit_resume_validated","repeat_noop","checksum_drift_refused","wrong_money_scale_refused","legacy_rows_and_single_ledger_preserved"))));
    }
    static int invoke(List<String> command,Map<String,String> env,String log)throws Exception{var builder=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(evidence.resolve(log).toFile());builder.environment().putAll(env);var p=builder.start();assertTrue(p.waitFor(30,java.util.concurrent.TimeUnit.SECONDS));return p.exitValue();}
    static byte[] pdf(List<List<String>> pages,boolean scan)throws Exception{
        try(var doc=new PDDocument();var out=new ByteArrayOutputStream()){
            for(var lines:pages){var page=new PDPage();doc.addPage(page);try(var content=new PDPageContentStream(doc,page)){
                if(scan){var image=new BufferedImage(1800,1500,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,1800,1500);g.setColor(Color.BLACK);g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,32));int y=100;for(String line:lines){g.drawString(line,35,y);y+=100;}g.dispose();content.drawImage(LosslessFactory.createFromImage(doc,image),0,220,612,510);}
                else{int y=740;for(String line:lines){content.beginText();content.setFont(new PDType1Font(Standard14Fonts.FontName.COURIER),8);content.newLineAtOffset(20,y);content.showText(line);content.endText();y-=70;}}
            }}doc.save(out);return out.toByteArray();}
    }
}
