package org.ruoyi.service.legal;
import org.apache.poi.xwpf.usermodel.*;
import org.ruoyi.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.time.*;
@Component
public class DocumentWord {
 public static String normalize(String s) {
  if(s==null||s.isBlank()||s.length()>200000)throw new ServiceException("正文不能为空且不能超过20万字",400);
  String text=LitigationDrafting.cleanDocument(s.replace("\r\n","\n").replace('\r','\n'));
  if(text.isBlank())throw new ServiceException("正文不能为空",400);
  if(TemplateDrafting.read(text)==null&&java.util.regex.Pattern.compile("\\{\\{[^{}]+}}").matcher(text).find())throw new ServiceException("正文仍包含未填写的模板字段，请补全后再保存或导出",400);
  return text;
 }
 public static String filename(String title) {
  String s=title==null?"法律文书":title.replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]","_").strip();
  if(s.isBlank())s="法律文书";if(s.length()>100)s=s.substring(0,100);
  return s+"-"+LocalDate.now(ZoneId.of("Asia/Shanghai"))+".docx";
 }
 public byte[] generate(String title,String content) {
  return build(title,normalize(content));
 }
 public byte[] generateTemplate(String content){
  if(content==null||content.isBlank()||content.length()>40000)throw new ServiceException("模板正文须为1至40000字",400);
  return build(null,content.replace("\r\n","\n").replace('\r','\n'));
 }
 private byte[] build(String title,String text){
  if(title!=null&&title.length()>200)throw new ServiceException("标题过长",400);
  try(var doc=new XWPFDocument();var out=new ByteArrayOutputStream()){
   var sect=doc.getDocument().getBody().addNewSectPr();
   var page=sect.addNewPgSz();page.setW(11906);page.setH(16838);
   var margins=sect.addNewPgMar();margins.setTop(1440);margins.setBottom(1440);margins.setLeft(1440);margins.setRight(1440);
   if(!text.startsWith("#")&&title!=null&&!title.isBlank())paragraph(doc,title,1);
   for(String line:text.split("\n",-1)){
    int level=line.matches("^#{1,6}\\s+.*")?line.indexOf(' '):0;
    String value=level>0?line.substring(level).stripLeading():line;
    paragraph(doc,value.replaceFirst("^\\s*[-*+]\\s+","• ").replaceFirst("^>\\s?",""),level);
   }
   doc.write(out);return out.toByteArray();
  }catch(Exception e){throw new ServiceException("导出失败，请稍后重试",500);}
 }
 private void paragraph(XWPFDocument doc,String text,int level){
  var p=doc.createParagraph();p.setSpacingAfter(120);p.setSpacingBetween(1.5);
  if(level>0){p.setStyle("Heading"+Math.min(level,3));p.setKeepNext(true);}
  if(level==1)p.setAlignment(ParagraphAlignment.CENTER);
  String[] parts=text.split("(\\*\\*)",-1);
  for(int i=0;i<parts.length;i++){var r=p.createRun();r.setFontFamily("宋体");r.setFontSize(level==1?20:level>0?16:12);r.setBold(level>0||i%2==1);r.setText(parts[i]);}
 }
}
