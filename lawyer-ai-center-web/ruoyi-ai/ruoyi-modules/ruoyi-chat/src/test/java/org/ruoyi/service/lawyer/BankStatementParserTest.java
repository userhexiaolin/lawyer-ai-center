package org.ruoyi.service.lawyer;

import org.junit.jupiter.api.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.ruoyi.service.legal.DocumentConversion;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class BankStatementParserTest {
    static final String HEADER="Date\tDirection\tAmount\tBalance\tCurrency\tRef\tAccount\tCounterparty\n";
    @Test void decimalRulesNeverRoundOrGuessUnknownAsZero(){
        assertEquals(new BigDecimal("-50000.00"),BankStatementParser.money("(￥50,000.00)"));
        assertEquals(new BigDecimal("0.10"),BankStatementParser.money("人民币 0.1 元"));
        assertNull(BankStatementParser.money("1,00.00"));assertNull(BankStatementParser.money("10.001"));assertNull(BankStatementParser.money("O.10"));assertNull(BankStatementParser.money("—"));assertNull(BankStatementParser.money("1e8"));assertNull(BankStatementParser.money("9999999999999999999.00"));
    }
    @Test void strictDatePreservesDateOnlyAndMissingSeconds(){
        assertEquals("2025-03-01",BankStatementParser.date("2025年3月1日"));assertEquals("2025-03-01T12:05",BankStatementParser.date("2025/3/1 12:05"));assertNull(BankStatementParser.date("2025-02-29"));assertNull(BankStatementParser.date("春节前后"));assertTrue(BankStatementParser.parse(HEADER+"2025-03\n").rows().isEmpty());
    }
    @Test void repeatedHeadersAndFootersDoNotBecomeTransactions(){
        var result=BankStatementParser.parse(HEADER+"2025-03-01\tincome\t2,000.00\t102000.00\tCNY\tSYN-A01\tA001\tPartyB\n"+HEADER+"2025-03-01\texpense\t(500.00)\t101500.00\tCNY\tSYN-A02\tA001\tPartyB\nTotal 2\n");
        assertEquals(2,result.rows().size());assertTrue(result.unparsed().isEmpty());assertEquals(1,result.warnings().size());assertEquals(new BigDecimal("500.00"),result.rows().get(1).amount());
        for(var r:result.rows())assertEquals(r.fragment(),(HEADER+"2025-03-01\tincome\t2,000.00\t102000.00\tCNY\tSYN-A01\tA001\tPartyB\n"+HEADER+"2025-03-01\texpense\t(500.00)\t101500.00\tCNY\tSYN-A02\tA001\tPartyB\nTotal 2\n").substring(r.start(),r.end()));
    }
    @Test void misalignedColumnsRemainPendingUnknownAndNegativeIncomeIsAnomaly(){
        var result=BankStatementParser.parse(HEADER+"2025-03-01 income -50.00 100.00 CNY X A B EXTRA\n2025-03-02\tincome\t-50.00\t50.00\tCNY\tY\tA\tB\n");
        assertEquals(2,result.rows().size());assertNull(result.rows().get(0).amount());assertTrue(result.rows().get(0).reasons().contains("COLUMN_MISALIGNMENT"));assertTrue(result.rows().get(1).anomalies().contains("DIRECTION_CONFLICT"));
    }
    @Test void csvQuotingKeepsNewlineAndFormulaAsInertText(){
        var rows=BankStatementParser.csvDocument("date,amount,memo\r\n2025-03-01,\"50,000.00\",\"line 1\nline \"\"2\"\"\"\r\n");assertEquals(2,rows.size());assertEquals("50,000.00",rows.get(1).get(1));assertEquals("line 1\nline \"2\"",rows.get(1).get(2));
        for(String value:List.of("=WEBSERVICE(1)"," +cmd","@SUM(A1)","-1+2","=HYPERLINK(1)\nnext","\uFEFF=CMD()","\u200B@SUM(A1)"))assertTrue(BankStatementExport.csvCell(value).startsWith("\"'"));
        assertThrows(DossierReadException.class,()->BankStatementParser.csvDocument("a,b\n\"unclosed"));
    }
    @Test void xlsxUsesExplicitSystemPaginationAndDoesNotEvaluateFormulas()throws Exception{
        byte[] bytes;try(var book=new XSSFWorkbook();var out=new ByteArrayOutputStream()){
            var sheet=book.createSheet("Synthetic");var header=sheet.createRow(0);header.createCell(0).setCellValue("Date");header.createCell(1).setCellValue("Amount");
            for(int i=1;i<=30;i++){var row=sheet.createRow(i);row.createCell(0).setCellValue("2025-03-01");row.createCell(1).setCellFormula("1+1");}book.write(out);bytes=out.toByteArray();}
        assertTrue(DossierFiles.validate("synthetic.xlsx",bytes).contains("spreadsheetml"));
        try(var source=new DossierFileReader(mock(DossierOcr.class),mock(DocumentConversion.class)).open(bytes,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")){
            assertEquals(2,source.count());assertTrue(source.label(2).contains("原件无纸页码"));assertTrue(source.read(1).text().contains("=1+1"));assertEquals(137,source.preview(2)[0]&255);
        }
    }
    @Test void coverageExportMasksOnlyContentAndPreservesVersionCoordinates()throws Exception{
        String version="90071992547409937",sensitive="SYNTHETIC phone 13800000000 account 6222020000000000000";
        var coverage=Map.of("fileVersionId",version,"pageIndex",3,"rowCount",0,"unparsedJson",BankStatementService.json(List.of("2:"+sensitive)),"warningsJson","[]","lines",List.of(Map.of("start",2,"end",10,"text",sensitive)));
        var legacySource=Map.of("rowId","17","fileVersionId",version,"fileName","legacy-synthetic.pdf","page",3,"fragment","SYNTHETIC","verified",true);
        String snapshot=BankStatementService.json(Map.of("rows",List.of(),"sources",List.of(legacySource),"duplicates",List.of(),"unparsed",List.of(coverage),"totals",Map.of("income","0.00"),"scope",Map.of(),"notice","SYNTHETIC ONLY","createdAt","2026-10-23","actorId","101","includePending",true));
        try(var book=new XSSFWorkbook(new ByteArrayInputStream(BankStatementExport.render(snapshot,"xlsx")))){
            var saved=new com.fasterxml.jackson.databind.ObjectMapper().readTree(book.getSheet("异常").getRow(1).getCell(2).getStringCellValue());
            assertEquals(version,saved.get("fileVersionId").asText());assertEquals(3,saved.get("pageIndex").asInt());
            assertEquals(2,saved.get("lines").get(0).get("start").asInt());assertEquals(10,saved.get("lines").get(0).get("end").asInt());
            for(String raw:List.of("13800000000","6222020000000000000")){assertFalse(saved.get("unparsedJson").asText().contains(raw));assertFalse(saved.get("lines").get(0).get("text").asText().contains(raw));}
            assertEquals("未记录（历史快照）",book.getSheet("来源").getRow(1).getCell(12).getStringCellValue());
        }
    }
    @Test void exportedWorkbookHasFourSheetsAndNoExecutableCell()throws Exception{
        var row=new LinkedHashMap<String,Object>();for(String key:List.of("id","reviewStatus","amount","summary","anomalyJson","reasonsJson"))row.put(key,key.equals("summary")?"=HYPERLINK(\"https://example.invalid\")":key.equals("amount")?"999999999999999999.99":key.endsWith("Json")?"[]":"pending_review");
        String snapshot=BankStatementService.json(Map.of("rows",List.of(row),"sources",List.of(),"duplicates",List.of(),"unparsed",List.of(),"totals",Map.of("income","0.00"),"scope",Map.of(),"notice","SYNTHETIC ONLY","createdAt","2026-10-21","actorId","101","includePending",true));
        byte[] bytes=BankStatementExport.render(snapshot,"xlsx");try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){assertEquals(4,book.getNumberOfSheets());assertEquals("来源",book.getSheetName(1));assertEquals("异常",book.getSheetName(2));assertEquals("导出说明",book.getSheetName(3));assertEquals("999999999999999999.99",book.getSheetAt(0).getRow(1).getCell(5).getStringCellValue());assertEquals(org.apache.poi.ss.usermodel.CellType.STRING,book.getSheetAt(0).getRow(1).getCell(12).getCellType());}
        assertTrue(new String(BankStatementExport.render(snapshot,"csv"),StandardCharsets.UTF_8).contains("'=HYPERLINK"));
    }
}
