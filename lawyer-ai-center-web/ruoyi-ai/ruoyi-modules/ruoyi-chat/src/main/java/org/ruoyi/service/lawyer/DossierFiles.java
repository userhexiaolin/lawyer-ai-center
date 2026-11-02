package org.ruoyi.service.lawyer;

import org.ruoyi.common.core.exception.ServiceException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;

/** Local format validation only. No document content, filenames or storage URLs are logged. */
public final class DossierFiles {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    private DossierFiles() {}
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    public static String name(String name) {
        if (name == null || name.isBlank() || name.length() > 255 || name.matches(".*[\\r\\n\\x00].*"))
            throw new ServiceException("文件名无效或超过255字", 400);
        return name.replace('\\', '/').substring(name.replace('\\', '/').lastIndexOf('/') + 1);
    }
    public static String extension(String name) { return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT); }
    public static void declared(String name, long bytes, String sha) {
        if (bytes < 1) throw new ServiceException("文件为空，请选择有内容的原件", 400);
        if (bytes > MAX_BYTES) throw new ServiceException("单份材料不能超过10MB，请拆分后上传", 413);
        if (!Set.of("pdf", "png", "jpg", "jpeg", "docx", "csv", "xlsx").contains(extension(name)))
            throw new ServiceException("支持PDF、DOCX、PNG、JPG、CSV、XLSX；ZIP请在本地安全解压后逐份上传", 415);
        if (sha == null || !sha.matches("[a-f0-9]{64}")) throw new ServiceException("文件校验指纹无效", 400);
    }
    public static String validate(String name, byte[] bytes) {
        declared(name, bytes.length, hash(bytes));
        String ext = extension(name);
        if(ext.equals("csv")){table(bytes,false);return "text/csv";}
        if(ext.equals("xlsx")&&starts(bytes,new byte[]{80,75,3,4})){safeOffice(bytes,"xl/workbook.xml");table(bytes,true);return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";}
        if (ext.equals("pdf") && starts(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))) return "application/pdf";
        if (ext.equals("docx") && starts(bytes, new byte[]{80,75,3,4})) {
            safeOffice(bytes,"word/document.xml"); return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if ((ext.equals("png") && starts(bytes, new byte[]{(byte)137,80,78,71,13,10,26,10}))
            || (Set.of("jpg","jpeg").contains(ext) && starts(bytes, new byte[]{(byte)255,(byte)216,(byte)255}))) {
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new IOException();
                var reader = readers.next();
                try { reader.setInput(input); if ((long)reader.getWidth(0)*reader.getHeight(0)>25_000_000) throw new IOException(); }
                finally { reader.dispose(); }
            } catch (IOException e) { throw new ServiceException("图片损坏或分辨率超过2500万像素",422); }
            return ext.equals("png") ? "image/png" : "image/jpeg";
        }
        throw new ServiceException("文件内容与扩展名不一致，请检查原件格式",415);
    }
    private static boolean starts(byte[] a, byte[] prefix) { return a.length>=prefix.length && Arrays.equals(a,0,prefix.length,prefix,0,prefix.length); }
    private static void table(byte[] bytes,boolean xlsx){try{DossierTableReader.open(bytes,xlsx);}catch(DossierReadException e){throw new ServiceException(e.getMessage(),422);}}
    private static void safeOffice(byte[] bytes,String required) {
        boolean content=false, document=false; int entries=0; long total=0;
        try (var zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry=zip.getNextEntry(); entry!=null; entry=zip.getNextEntry()) {
                String path=entry.getName().replace('\\','/');
                if (++entries>1000 || path.startsWith("/") || path.matches("^[A-Za-z]:.*") || Arrays.asList(path.split("/")).contains("..") || path.toLowerCase(Locale.ROOT).contains("vbaproject"))
                    throw new IOException();
                content|=path.equals("[Content_Types].xml"); document|=path.equals(required);
                byte[] buffer=new byte[8192]; int n;
                while ((n=zip.read(buffer))!=-1) { total+=n; if(total>20*1024*1024 || total>Math.max(1024*1024,(long)bytes.length*100)) throw new IOException(); }
            }
            if(!content||!document)throw new IOException();
        } catch(IOException e) { throw new ServiceException("DOCX损坏、含不安全路径/宏或解压体积超限，请转换为PDF后重试",422); }
    }
}
