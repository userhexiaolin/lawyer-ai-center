package org.ruoyi.service.legal;

import org.ruoyi.service.legal.TemplateTypeDecisionStore.Decision;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Classification-only policy. It never edits the original or treats a suggestion as confirmation. */
final class TemplateTypePolicy {
 static final String VERSION="stable-type-v2-compatibility";
 private TemplateTypePolicy(){}
 static String canonical(String text){
  if(text==null||text.isBlank())throw new IllegalArgumentException("Empty template text");
  return text.replace("\r\n","\n").replace('\r','\n').strip();
 }
 static String digest(String text){
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
  catch(Exception e){throw new IllegalStateException(e);}
 }
 private record Match(String type,String keyword){}
 private static List<String> keywords(String type){
  return switch(type){
   case "合同文书"->List.of(type,"合同","协议");
   case "民事起诉状"->List.of(type,"起诉状");
   case "民事答辩状"->List.of(type,"答辩状");
   default->List.of(type);
  };
 }
 static Decision title(String text,List<String> allowed){
  for(String line:text.lines().map(String::strip).filter(s->!s.isBlank()).limit(5).toList()){
   if(line.length()>100||line.matches(".*[，。！？；：:,!?;].*")||line.matches(".*(什么是|如何|目录|合集|清单|汇编).*"))continue;
   String heading=line.replaceFirst("^[#\\s\uFEFF]+","").replaceAll("\\s+","");
   String core=heading.replaceFirst("[（(][^（）()]{0,40}[）)]$","").replaceFirst("(?:通用)?(?:模板|范本|样本|示范文本)$","");
   var matches=new ArrayList<Match>();
   for(String type:allowed){
    if(Set.of("民事起诉状","民事答辩状").contains(type)&&core.matches(".*(刑事|行政|仲裁).*"))continue;
    for(String keyword:keywords(type))if(!keyword.isBlank()&&core.endsWith(keyword))matches.add(new Match(type,keyword));
   }
   if(matches.isEmpty())continue;
   if(core.matches(".*[及和与、/&暨].*")){
    var forms=new HashSet<String>();
    for(String type:allowed)for(String keyword:keywords(type))if(core.contains(keyword))forms.add(type);
    // A long custom type naturally contains its generic family; independent forms do not.
    for(var match:matches)forms.removeIf(type->!type.equals(match.type())&&keywords(type).stream().allMatch(k->match.keyword().contains(k)||!core.contains(k)));
    if(forms.size()>1)return new Decision(null,"ABSTAIN","");
   }
   int longest=matches.stream().mapToInt(m->m.keyword().length()).max().orElse(0);
   var best=matches.stream().filter(m->m.keyword().length()==longest).map(Match::type).distinct().toList();
   if(best.size()==1)return new Decision(best.get(0),"TITLE",line);
   return new Decision(null,"ABSTAIN","");
  }
  return null;
 }
 static boolean valid(Decision decision,List<String> allowed,String excerpt){
  if(decision==null)return false;
  if("ABSTAIN".equals(decision.method()))return decision.documentType()==null&&"".equals(decision.evidence());
  return Set.of("TITLE","AI").contains(Objects.toString(decision.method(),""))&&allowed.contains(decision.documentType())
   &&TemplateTypeCompatibility.error(decision.documentType(),null,excerpt).isEmpty()
   &&decision.evidence()!=null&&!decision.evidence().isBlank()&&decision.evidence().length()<=120&&excerpt.contains(decision.evidence());
 }
 static String warning(Decision decision,boolean reused){
  if(decision.documentType()==null)return "正文依据不足或存在歧义，保留当前类型；请人工选择";
  String basis="TITLE".equals(decision.method())?"正文标题匹配":"AI建议依据原文";
  return basis+"“"+decision.evidence()+"”；待人工核对"+(reused?"（复用相同内容的识别建议）":"");
 }
}
