package org.ruoyi.service.legal;
import com.fasterxml.jackson.databind.*;
import dev.langchain4j.model.chat.response.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.*;

/** Citation transport records are not part of a delivered legal document. */
public final class DocumentCitations {
 private DocumentCitations(){}
 private static final ObjectMapper JSON=new ObjectMapper();
 private static final Set<String> KEYS=Set.of("type","lawName","articleNo","status","caseNo","title","court","sourceUrl","fragmentId","law_name","article_no","validityStatus","validity_status","case_no","source_url","fragment_id","url");
 private static final Pattern FENCES=Pattern.compile("```[^\\n]*\\r?\\n([\\s\\S]*?)```");
 public static final String SYSTEM_PROMPT="本次为正式文书起草，只输出可编辑的文书正文，不输出JSON、代码围栏、legal-citations、fragmentId、sourceUrl等引用元数据。只依据本次起草要素，不混入旧会话或模板示例的事实；不得推断实际违约、增加金额或请求。法律依据如需引用，应在正文自然表述，无法核实的法条不写具体条号。缺失资料标【待补充】，不追问。"+DraftingFacts.PROMPT;
 public static boolean applies(String request){return request!=null&&request.contains("---起草要素---");}
 private static boolean text(JsonNode value,String key){return value.path(key).isTextual()&&!value.path(key).asText().isBlank();}
 private static boolean record(JsonNode value){
  if(!value.isObject())return false;var names=value.fieldNames();while(names.hasNext())if(!KEYS.contains(names.next()))return false;
  return ((text(value,"lawName")||text(value,"law_name"))&&(text(value,"articleNo")||text(value,"article_no")))||text(value,"caseNo")||text(value,"case_no");
 }
 private static boolean metadata(String raw){try{var value=JSON.readTree(raw);if(value.isArray()){if(value.isEmpty())return false;for(var item:value)if(!record(item))return false;return true;}return record(value);}catch(Exception ignored){return false;}}
 private static String plain(String value,boolean[] removed){
  var output=new StringBuilder();int position=0,budget=Math.max(100000,value.length()*8);
  for(int start=0;start<value.length();start++){
   char initial=value.charAt(start);if(initial!='{'&&initial!='[')continue;
   var stack=new ArrayDeque<Character>();boolean quoted=false,escaped=false;int end=-1;
   for(int i=start;i<value.length();i++){
    if(--budget<0)return output.append(value.substring(position)).toString();
    char c=value.charAt(i);if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='"')quoted=false;continue;}
    if(c=='"'){quoted=true;continue;}if(c=='{'||c=='[')stack.push(c);
    if(c=='}'||c==']'){if(stack.isEmpty()||stack.pop()!=(c=='}'?'{':'['))break;if(stack.isEmpty()){end=i+1;break;}}
   }
   if(end<0)continue;String raw=value.substring(start,end);output.append(value,position,start);
   if(metadata(raw))removed[0]=true;else output.append(raw);position=end;start=end-1;
  }
  return output.append(value.substring(position)).toString();
 }
 public static String clean(String raw){
  String value=Objects.toString(raw,"").strip().replaceFirst("(?is)^```(?:markdown|md|text|plaintext)?\\s*\\n([\\s\\S]*?)\\n?```\\s*$","$1").strip();
  boolean[] removed={false};var matcher=FENCES.matcher(value);var output=new StringBuilder();int position=0,budget=Math.max(100000,value.length()*8);
  while(matcher.find()){output.append(plain(value.substring(position,matcher.start()),removed));if(metadata(matcher.group(1)))removed[0]=true;else output.append(matcher.group());position=matcher.end();}
  String result=output.append(plain(value.substring(position),removed)).toString().strip();return removed[0]?result.replaceFirst("\\n\\s*---\\s*$","").strip():result;
 }
 public static StreamingChatResponseHandler wrap(StreamingChatResponseHandler delegate){return wrap(delegate,null,null);}
 public static StreamingChatResponseHandler wrap(StreamingChatResponseHandler delegate,String request,java.util.function.BiConsumer<String,StreamingChatResponseHandler> repair){return new StreamingChatResponseHandler(){
  private final StringBuilder raw=new StringBuilder();private final AtomicBoolean terminal=new AtomicBoolean();
  public void onPartialResponse(String text){if(!terminal.get())raw.append(text);}
  public void onCompleteResponse(ChatResponse response){if(!terminal.compareAndSet(false,true))return;try{String value=clean(raw.isEmpty()&&response!=null?response.aiMessage().text():raw.toString());DraftingFacts.requireGrounded(value,request);delegate.onPartialResponse(value);delegate.onCompleteResponse(response);}catch(DraftingException e){if(repair==null)delegate.onError(e);else try{repair.accept(e.getMessage(),wrap(delegate,request,null));}catch(Exception failure){delegate.onError(failure);}}}
  public void onError(Throwable error){if(terminal.compareAndSet(false,true))delegate.onError(error);}
 };}
}
