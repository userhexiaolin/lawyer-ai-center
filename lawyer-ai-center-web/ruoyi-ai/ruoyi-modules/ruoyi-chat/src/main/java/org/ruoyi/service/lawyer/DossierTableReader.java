package org.ruoyi.service.lawyer;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/** Safe local tabular input; stable rendered pages are explicitly system pagination, not bank paper pages. */
public final class DossierTableReader {
    public record Page(String label,String text){
        BufferedImage image(){
            var image=new BufferedImage(2000,1400,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
            g.setColor(Color.WHITE);g.fillRect(0,0,2000,1400);g.setColor(Color.BLACK);g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,18));
            int y=40;for(String line:text.split("\n")){String[] cols=line.split("\t",-1);int width=1940/Math.max(1,cols.length);for(int c=0;c<cols.length;c++){var clipped=(Graphics2D)g.create(30+c*width,y-22,width-5,35);clipped.drawString(cols[c],0,23);clipped.dispose();}y+=40;}g.dispose();return image;
        }
    }
    private DossierTableReader(){}
    public static String csvText(byte[] bytes){
        if(bytes.length==0)throw new DossierReadException("CSV_INVALID","CSV为空");
        String value;
        try{value=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
        catch(CharacterCodingException utf8){try{value=Charset.forName("GB18030").newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}catch(Exception e){throw new DossierReadException("CSV_ENCODING","请将CSV保存为UTF-8或GB18030后重试");}}
        value=value.replaceFirst("^\\uFEFF","");if(value.chars().anyMatch(c->c==0||c<9||c>13&&c<32))throw new DossierReadException("CSV_BINARY","CSV含二进制控制字符");return value;
    }
    public static List<Page> open(byte[] bytes,boolean xlsx){
        var pages=new ArrayList<Page>();
        if(!xlsx){var rows=BankStatementParser.csvDocument(csvText(bytes));if(rows.isEmpty()||rows.get(0).size()<2)throw new DossierReadException("CSV_INVALID","CSV需要至少两列与表头");paginate(pages,"CSV",rows);}
        else try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(bytes))){
            if(workbook.getNumberOfSheets()>50)throw new DossierReadException("TABLE_LIMIT","工作表超过50个，请拆分");
            var formatter=new DataFormatter(Locale.ROOT);int total=0;
            for(var sheet:workbook){var rows=new ArrayList<List<String>>();
                if(sheet.getLastRowNum()>10000)throw new DossierReadException("TABLE_LIMIT","工作表原始行号超过10000，请拆分");
                for(int index=0;index<=sheet.getLastRowNum();index++){var row=sheet.getRow(index);var values=new ArrayList<String>();if(row!=null){if(row.getLastCellNum()>50)throw new DossierReadException("TABLE_LIMIT","列数超过50，请拆分");for(int col=0;col<row.getLastCellNum();col++){var cell=row.getCell(col);String value=cell==null?"":cell.getCellType()==CellType.FORMULA?"="+cell.getCellFormula():formatter.formatCellValue(cell);if(value.length()>50000)throw new DossierReadException("TABLE_LIMIT","单元格过长");values.add(value);}}
                    rows.add(values);if(++total>10000)throw new DossierReadException("TABLE_LIMIT","表格超过10000行，请拆分");}
                if(rows.stream().anyMatch(r->r.stream().anyMatch(s->!s.isBlank())))paginate(pages,sheet.getSheetName(),rows);
            }
        }catch(DossierReadException e){throw e;}catch(Exception e){throw new DossierReadException("XLSX_INVALID","XLSX不可读或加密，请另存无密码的XLSX后上传");}
        if(pages.isEmpty())throw new DossierReadException("TABLE_EMPTY","表格无可用内容");if(pages.size()>200)throw new DossierReadException("PAGE_LIMIT","表格系统分页超过200页，请拆分");return List.copyOf(pages);
    }
    static void paginate(List<Page> pages,String sheet,List<List<String>> rows){
        int header=0;while(header<rows.size()&&rows.get(header).stream().allMatch(String::isBlank))header++;
        if(header==rows.size())return;
        for(int i=header;i<rows.size();i++){var fields=rows.get(i).stream().map(BankStatementParser::field).toList();if(fields.contains("date")&&(fields.contains("amount")||fields.contains("income")||fields.contains("expense"))){header=i;break;}}
        for(int start=0;start<rows.size();start+=28){
            String label="系统分页 · 工作表 "+sheet+" · 原始记录行 "+(start+1)+"–"+Math.min(rows.size(),start+28)+"（原件无纸页码）";
            var text=new StringBuilder("工作表 "+sheet+"\n系统分页 原件无纸页码\n");text.append("原始记录行\t").append(line(rows.get(header))).append('\n');
            for(int i=start;i<Math.min(rows.size(),start+28);i++)if(i!=header)text.append(i+1).append('\t').append(line(rows.get(i))).append('\n');
            pages.add(new Page(label,text.toString()));if(pages.size()>200)throw new DossierReadException("PAGE_LIMIT","表格系统分页超过200页，请拆分");
        }
    }
    static String line(List<String> row){return row.stream().map(s->s.replace("\\","\\\\").replace("\t","\\t").replace("\r","").replace("\n","\\n")).collect(java.util.stream.Collectors.joining("\t"));}
}
