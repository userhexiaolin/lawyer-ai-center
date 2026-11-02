package org.ruoyi.service.lawyer;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

/** OS-local recognizer; private temporary files are removed even on timeout. No shell interpolation. */
@Component
public class LocalDossierOcr implements DossierOcr {
    @Value("${legal.dossier.ocr-language:zh-Hans-CN}") private String language="zh-Hans-CN";
    @Override public String recognize(BufferedImage image) {
        if (!System.getProperty("os.name","").toLowerCase().contains("windows"))
            throw new DossierReadException("OCR_UNAVAILABLE","服务器未配置本地OCR，请人工查看原件或在Windows本地部署启用识别");
        Path dir=null; Process process=null;
        try {
            dir=Files.createTempDirectory("dossier-ocr-");
            Path script=dir.resolve("ocr.ps1"), input=dir.resolve("page.png"), output=dir.resolve("text.txt");
            try(var resource=getClass().getResourceAsStream("/dossier/windows-ocr.ps1")) {
                if(resource==null)throw new IllegalStateException(); Files.copy(resource,script);
            }
            ImageIO.write(image,"png",input.toFile());
            process=new ProcessBuilder("powershell.exe","-NoLogo","-NoProfile","-NonInteractive","-ExecutionPolicy","Bypass","-File",script.toString(),"-Image",input.toString(),"-Output",output.toString(),"-Language",language)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if(!process.waitFor(60,TimeUnit.SECONDS))throw new DossierReadException("OCR_TIMEOUT","本页文字识别超时，可仅重试本页");
            if(process.exitValue()!=0||!Files.isRegularFile(output))throw new DossierReadException("OCR_UNAVAILABLE","本地OCR或语言包不可用，请安装对应Windows识别语言包后重试");
            if(Files.size(output)>1024*1024)throw new DossierReadException("OCR_LIMIT","本页识别文字过长，请人工处理");
            return Files.readString(output,java.nio.charset.StandardCharsets.UTF_8).replace("\uFEFF","").strip();
        }catch(DossierReadException e){throw e;}
        catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();throw new DossierReadException("OCR_UNAVAILABLE","本地文字识别不可用，请稍后重试或人工查看原件");}
        finally {if(process!=null&&process.isAlive())process.destroyForcibly();if(dir!=null){try(var files=Files.list(dir)){files.forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}catch(Exception ignored){}try{Files.deleteIfExists(dir);}catch(Exception ignored){}}}
    }
}
