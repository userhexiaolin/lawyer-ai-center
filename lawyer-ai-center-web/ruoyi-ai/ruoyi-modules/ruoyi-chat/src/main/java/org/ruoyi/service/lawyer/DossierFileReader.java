package org.ruoyi.service.lawyer;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ruoyi.service.legal.DocumentConversion;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

@Component @RequiredArgsConstructor
public class DossierFileReader {
    public static final String VERSION="pdfbox-page/local-wordrows+table-v3";
    private final DossierOcr ocr;
    private final DocumentConversion conversion;
    public record Page(String text,String method,String ocrStatus,String previewStatus,String errorCode,String errorMessage) {
        public boolean usable(){return errorCode==null;}
    }
    public Source open(byte[] bytes,String mime) {
        try {
            if(mime.equals("text/csv")||mime.contains("spreadsheetml"))return new Source(DossierTableReader.open(bytes,mime.contains("spreadsheetml")));
            if(mime.contains("wordprocessingml"))bytes=conversion.convert(bytes,"docx","pdf");
            if(mime.startsWith("image/")){
                var image=ImageIO.read(new ByteArrayInputStream(bytes));if(image==null)throw new IOException();return new Source(null,image);
            }
            var document=Loader.loadPDF(bytes);
            if(document.getNumberOfPages()<1||document.getNumberOfPages()>200){document.close();throw new DossierReadException("PAGE_LIMIT","PDF页数无效或超过200页，请拆分后重试");}
            return new Source(document,null);
        } catch(InvalidPasswordException e){throw new DossierReadException("ENCRYPTED_PDF","PDF需要密码；原件已保存，请上传解锁副本或人工查看");}
        catch(DossierReadException e){throw e;}
        catch(Exception e){throw new DossierReadException(mime.contains("wordprocessingml")?"CONVERSION_FAILED":"MALFORMED_DOCUMENT",mime.contains("wordprocessingml")?"Word排版转换失败，请检查本地转换器或转成PDF后上传":"原件损坏或不可解析；原件仍可下载，请核对并替换副本");}
    }
    public final class Source implements AutoCloseable {
        private final PDDocument pdf;private final BufferedImage image;
        private final java.util.List<DossierTableReader.Page> tables;
        Source(PDDocument pdf,BufferedImage image){this.pdf=pdf;this.image=image;this.tables=null;}
        Source(java.util.List<DossierTableReader.Page> tables){this.pdf=null;this.image=null;this.tables=tables;}
        public int count(){return tables!=null?tables.size():pdf==null?1:pdf.getNumberOfPages();}
        public String label(int index){return tables==null?null:tables.get(index-1).label();}
        public BufferedImage image(int index)throws IOException{return render(index,2000);}
        private BufferedImage render(int index,int limit)throws IOException{
            if(index<1||index>count())throw new IOException();
            if(tables!=null)return tables.get(index-1).image();
            if(pdf==null){
                if(Math.max(image.getWidth(),image.getHeight())<=limit)return image;
                float scale=(float)limit/Math.max(image.getWidth(),image.getHeight());
                var reduced=new BufferedImage(Math.max(1,(int)(image.getWidth()*scale)),Math.max(1,(int)(image.getHeight()*scale)),BufferedImage.TYPE_INT_RGB);
                var g=reduced.createGraphics();g.drawImage(image,0,0,reduced.getWidth(),reduced.getHeight(),null);g.dispose();return reduced;
            }
            var box=pdf.getPage(index-1).getCropBox();
            float scale=Math.min(limit>2000?4f:2f,(float)limit/Math.max(box.getWidth(),box.getHeight()));
            return new PDFRenderer(pdf).renderImage(index-1,scale,org.apache.pdfbox.rendering.ImageType.RGB);
        }
        public byte[] preview(int index)throws IOException{var out=new ByteArrayOutputStream();ImageIO.write(image(index),"png",out);return out.toByteArray();}
        public Page read(int index) {
            if(tables!=null){String text=tables.get(index-1).text();return text.length()>50000?new Page("","table_text","not_needed","success","PAGE_TEXT_LIMIT","此系统页超过50000字，请缩小列宽内容或拆分原表后重试"):new Page(text,"table_text","not_needed","success",null,null);}
            String text="",method="text",preview="success";
            try {if(pdf!=null){var stripper=new PDFTextStripper();stripper.setStartPage(index);stripper.setEndPage(index);text=stripper.getText(pdf).strip();}}
            catch(Exception e){text="";}
            BufferedImage rendered=null;
            try{rendered=render(index,text.isBlank()?2500:2000);}catch(Exception e){preview="failed";}
            try {
                if(text.isBlank()){
                    method="ocr";
                    if(rendered==null)throw new DossierReadException("PREVIEW_FAILED","无法读取本页图像，请下载原件人工检查或重试");
                    text=ocr.recognize(rendered);
                }
                if(text==null||text.isBlank())throw new DossierReadException("EMPTY_UNCONFIRMED","未识别到文字；可能是空白或漏读，请人工核实空白页或重试");
                if(text.length()>50000)throw new DossierReadException("PAGE_TEXT_LIMIT","本页文字超过50000字，请拆分或人工处理");
                return new Page(text,method,method.equals("ocr")?"success":"not_needed",preview,null,null);
            }catch(DossierReadException e){return new Page("",method,method.equals("ocr")?"failed":"not_needed",preview,e.errorCode(),e.getMessage());}
            catch(Exception e){return new Page("",method,method.equals("ocr")?"failed":"not_needed",preview,"PAGE_READ_FAILED","本页读取暂不可用，请重试或下载原件核对");}
        }
        public void close()throws IOException{if(pdf!=null)pdf.close();}
    }
}
