package org.ruoyi.service.legal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** Local conversion with an application-owned office instance and private per-request files. */
@Component
public class DocumentConversion {
    @Value("${legal.documents.soffice:}") private String soffice = "";
    private final ReentrantLock conversionLock=new ReentrantLock();
    private Path profile;
    private volatile Process active;
    private volatile Process office;
    private volatile boolean closed;
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void warmup(){java.util.concurrent.CompletableFuture.runAsync(()->{try{convert(new DocumentWord().generate(null,"文档转换检查"),"docx","pdf");}catch(Exception ignored){/* Export returns an actionable error if the runtime is unavailable. */}});}
    @jakarta.annotation.PreDestroy
    public void close(){closed=true;stop(active);stop(office);if(profile!=null)remove(profile);}
    private static void stop(Process process){if(process!=null){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();}}
    private void startOffice(String executable)throws Exception{
        if(office!=null&&office.isAlive())return;
        var command=List.of(executable,"-env:UserInstallation="+profile.toUri(),"--headless","--norestore","--nodefault","--nofirststartwizard","--nologo","--accept=pipe,name=legal_"+UUID.randomUUID().toString().replace("-","")+";urp;StarOffice.ServiceManager");
        office=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(profile.resolve("office.log").toFile())).start();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(600);
        while(!Files.exists(profile.resolve(".lock"))){if(!office.isAlive()||closed||System.nanoTime()>deadline)throw new DraftingException("文档转换进程启动失败，请检查转换服务",503);Thread.sleep(100);}
    }
    public byte[] convert(byte[] source, String extension, String target) {
        if (!Set.of("docx","pdf","doc","rtf","odt").contains(extension) || !Set.of("docx","pdf").contains(target))
            throw new DraftingException("不支持的文档转换格式",400);
        Path dir=null;
        boolean locked=false;
        try {
            if(!conversionLock.tryLock(600,TimeUnit.SECONDS))throw new DraftingException("文档转换繁忙，请稍后重试",503);locked=true;
            if(closed)throw new DraftingException("文档转换服务已关闭",503);
            dir=Files.createTempDirectory("legal-convert-");
            Path input=dir.resolve("source."+extension), output=dir.resolve("source."+target), log=dir.resolve("conversion.log");
            Files.write(input,source);
            String executable=soffice.isBlank()?findOffice():soffice;
            if(executable.toLowerCase(Locale.ROOT).endsWith("soffice.exe")){
                Path console=Path.of(executable).resolveSibling("soffice.com");if(Files.isRegularFile(console))executable=console.toString();
            }
            List<String> command;
            if (!executable.isBlank()) {
                if(profile==null)profile=Files.createTempDirectory("legal-office-profile-");
                startOffice(executable);
                Path results=Files.createDirectory(dir.resolve("out")); output=results.resolve("source."+target);
                command=new ArrayList<>(List.of(executable,"-env:UserInstallation="+profile.toUri(),"--headless","--norestore","--nodefault","--nofirststartwizard","--nologo"));
                if(extension.equals("pdf"))command.add("--infilter=writer_pdf_import");
                command.addAll(List.of("--convert-to",target.equals("docx")?"docx:Office Open XML Text":"pdf:writer_pdf_Export","--outdir",results.toString(),input.toString()));
            } else throw new DraftingException("服务器未配置文档转换器，请配置 legal.documents.soffice 后重试",503);
            Process process=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            active=process;
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(600);
            while(System.nanoTime()<deadline&&!closed){
                byte[] completed=completed(output,target);
                if(completed!=null){stop(process);return completed;}
                if(!process.isAlive()&&process.exitValue()!=0)throw new DraftingException("文档转换失败，请检查本地LibreOffice转换服务",503);
                Thread.sleep(100);
            }
            stop(process);stop(office);office=null;throw new DraftingException("文档转换超时，请检查转换服务后重试",503);
        } catch(DraftingException e){throw e;} catch(Exception e){throw new DraftingException("文档转换服务不可用，请检查本地 Word/LibreOffice",503);}
        finally {if(dir!=null)remove(dir);if(locked){active=null;conversionLock.unlock();}}
    }
    private static byte[] completed(Path path,String target){
        try{
            if(!Files.isRegularFile(path)||Files.size(path)<5)return null;
            byte[] bytes=Files.readAllBytes(path);
            if(target.equals("pdf")){
                String tail=new String(bytes,Math.max(0,bytes.length-128),Math.min(bytes.length,128),java.nio.charset.StandardCharsets.ISO_8859_1);
                if(!tail.stripTrailing().endsWith("%%EOF"))return null;
                try(var pdf=org.apache.pdfbox.Loader.loadPDF(bytes)){if(pdf.getNumberOfPages()<1)return null;}
            }else try(var zip=new java.util.zip.ZipFile(path.toFile())){if(zip.getEntry("word/document.xml")==null||zip.getEntry("[Content_Types].xml")==null)return null;}
            return bytes;
        }catch(Exception writing){return null;}
    }
    private static void remove(Path dir){try(var paths=Files.walk(dir)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}catch(Exception ignored){}}
    private String findOffice(){
        for(Path root=Path.of("").toAbsolutePath();root!=null;root=root.getParent()){
            for(String name:List.of("data/document-converter/runtime/program/soffice.exe","data/document-converter/runtime/LibreOffice/program/soffice.exe"))if(Files.isRegularFile(root.resolve(name)))return root.resolve(name).toString();
        }
        for(String candidate:List.of("../data/document-converter/runtime/LibreOffice/program/soffice.exe","data/document-converter/runtime/LibreOffice/program/soffice.exe","../data/document-converter/runtime/program/soffice.exe","data/document-converter/runtime/program/soffice.exe","C:/Program Files/LibreOffice/program/soffice.exe","C:/Program Files (x86)/LibreOffice/program/soffice.exe","/usr/bin/libreoffice","/usr/bin/soffice"))if(Files.isRegularFile(Path.of(candidate)))return Path.of(candidate).toAbsolutePath().normalize().toString();
        return "";
    }
}
