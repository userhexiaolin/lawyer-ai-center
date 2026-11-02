package org.ruoyi.service.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.rag.content.Content;
import org.ruoyi.domain.vo.knowledge.LegalReference;

import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/** Sources come only from this request's permission-checked, selected RAG context. Never query by model IDs. */
public final class ContractReviewSources {
    private ContractReviewSources() {}
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern ARTICLE = Pattern.compile("^(?:第)?([0-9零〇一二两三四五六七八九十百千万]+)(?:条)?(?:之([0-9零〇一二两三四五六七八九十百千万]+))?$");
    private static final Pattern TEXT_LAW = Pattern.compile("^《([^》]+)》\\s*(第[0-9零〇一二两三四五六七八九十百千万]+条(?:之[0-9零〇一二两三四五六七八九十百千万]+)?)$");
    private static String text(JsonNode node) { return node.isTextual() ? node.asText().strip() : ""; }
    private static String clean(String text) { return Objects.toString(text, "").strip(); }
    private static String http(String url) {
        try { var uri=URI.create(clean(url)); return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) && uri.getHost()!=null ? uri.toString() : ""; }
        catch(IllegalArgumentException e) { return ""; }
    }
    /** Read structured metadata, not reference-looking instructions inside retrieved prose. */
    public static List<LegalReference> references(List<Content> contents) {
        if(contents==null) return List.of();
        List<LegalReference> refs=new ArrayList<>();
        for(var content:contents) {
            var m=content.textSegment().metadata();
            String law=clean(m.getString("law_name")), article=clean(m.getString("article_no")), caseNo=clean(m.getString("case_no"));
            if(caseNo.isEmpty() && (law.isEmpty() || article.isEmpty())) continue;
            String fid=clean(m.getString("fragmentId")); if(fid.isEmpty()) fid=clean(m.getString("fid"));
            refs.add(new LegalReference(caseNo.isEmpty()?"law":"case",law,article,clean(m.getString("status")),caseNo,null,null,http(m.getString("source_url")),fid));
        }
        return List.copyOf(refs);
    }
    private static String number(String source) {
        if(source.matches("[0-9]+")) {try{return String.valueOf(Integer.parseInt(source));}catch(NumberFormatException e){return "";}}
        if(!source.matches(".*[十百千万].*")) {
            StringBuilder digits=new StringBuilder();
            for(char c:source.toCharArray()){int n="零一二三四五六七八九".indexOf(c);if(c=='〇')n=0;if(c=='两')n=2;if(n<0)return "";digits.append(n);}
            return number(digits.toString());
        }
        int total=0,section=0,digit=0;
        for(char c:source.toCharArray()) {
            int n="零一二三四五六七八九".indexOf(c); if(c=='〇')n=0;if(c=='两')n=2;
            if(n>=0) {digit=n;continue;}
            int unit=switch(c){case '十'->10;case '百'->100;case '千'->1000;case '万'->10000;default->0;};
            if(unit==0)return "";
            if(unit==10000){section+=digit;total+=(section==0?1:section)*unit;section=0;digit=0;}
            else {section+=(digit==0?1:digit)*unit;digit=0;}
        }
        return String.valueOf(total+section+digit);
    }
    private static String lawKey(String name,String article) {
        String law=clean(name).replaceAll("[《》\\s]","").replaceFirst("^中华人民共和国","");
        var match=ARTICLE.matcher(clean(article).replaceAll("\\s", ""));
        if(law.isEmpty() || !match.matches())return "";
        String n=number(match.group(1));if(n.isEmpty())return "";
        return law+"|"+n+(match.group(2)==null?"":"之"+number(match.group(2)));
    }
    private static String caseKey(String caseNo) { return clean(caseNo).replaceAll("\\s","").replace('（','(').replace('）',')'); }
    private static JsonNode alias(JsonNode ref,String camel,String snake,String shortKey) {
        return ref.has(camel)?ref.path(camel):ref.has(snake)?ref.path(snake):ref.path(shortKey);
    }
    private static boolean usable(LegalReference ref) { return !clean(ref.fragmentId()).isEmpty() || !http(ref.sourceUrl()).isEmpty(); }
    private static void index(Map<String,LegalReference> refs,String key,LegalReference ref) {
        if(!key.isEmpty())refs.merge(key,ref,(a,b)->!usable(a)&&usable(b)?b:a);
    }
    private static void fill(ObjectNode ref,LegalReference hit) {
        // Clear all model-provided source aliases, including plausible but unverified URLs/IDs.
        ref.remove(List.of("fragmentId","fragment_id","sourceUrl","source_url","url","sourceOrigin","source_origin"));
        ref.put("sourceOrigin",hit==null?"unmatched":"retrieval_backfill");
        if(hit==null) {if(!"case".equals(text(ref.path("type"))))ref.put("status","unknown");return;}
        if("law".equals(hit.type())) {ref.put("lawName",hit.lawName());ref.put("articleNo",hit.articleNo());ref.put("status",clean(hit.status()).isEmpty()?"unknown":hit.status());}
        else ref.put("caseNo",hit.caseNo());
        if(!clean(hit.fragmentId()).isEmpty())ref.put("fragmentId",hit.fragmentId());
        String url=http(hit.sourceUrl());if(!url.isEmpty()){ref.put("sourceUrl",url);ref.put("url",url);}
    }
    private static void enrichRefs(ObjectNode issue,String field,Map<String,LegalReference> hits,boolean cases) {
        var refs=JSON.createArrayNode();
        for(var item:issue.path(field)) {
            ObjectNode ref;
            if(item.isObject())ref=((ObjectNode)item).deepCopy();
            else if(!cases && item.isTextual()) {
                var match=TEXT_LAW.matcher(text(item));
                if(!match.matches()){refs.add(item);continue;}
                ref=JSON.createObjectNode().put("lawName",match.group(1)).put("articleNo",match.group(2));
            } else {refs.add(item);continue;}
            ref.put("type",cases?"case":"law");
            String key=cases?caseKey(text(alias(ref,"caseNo","case_no","caseNo"))):lawKey(text(alias(ref,"lawName","law_name","name")),text(alias(ref,"articleNo","article_no","article")));
            fill(ref,hits.get(key));refs.add(ref);
        }
        issue.set(field,refs);
    }
    /** Returns a unified issues VO. Malformed/incomplete output retains the existing text fallback. */
    public static String enrich(String raw,List<LegalReference> selected) {
        try {
            var result=(ObjectNode)ContractReviewReport.parse(raw);
            Map<String,LegalReference> laws=new LinkedHashMap<>(),cases=new LinkedHashMap<>();
            for(var ref:selected) {
                if("case".equals(ref.type()))index(cases,caseKey(ref.caseNo()),ref);
                else index(laws,lawKey(ref.lawName(),ref.articleNo()),ref);
            }
            for(var item:result.path("issues")) {
                var issue=(ObjectNode)item;
                enrichRefs(issue,"lawRefs",laws,false);enrichRefs(issue,"caseRefs",cases,true);
                issue.remove(List.of("legalBasis","law_refs","case_refs","citations"));
            }
            result.remove("risks");
            return JSON.writeValueAsString(result);
        } catch(Exception invalid) {return raw;}
    }
    /** Emit and persist the same final enriched JSON, without introducing a replacement-event protocol. */
    public static StreamingChatResponseHandler wrap(StreamingChatResponseHandler delegate,List<LegalReference> selected) {
        return wrap(delegate,selected,"",false);
    }
    public static StreamingChatResponseHandler wrap(StreamingChatResponseHandler delegate,List<LegalReference> selected,String source,boolean qualityEnabled) {
        var sources=List.copyOf(selected);
        return new StreamingChatResponseHandler() {
            private final StringBuilder raw=new StringBuilder();
            private final AtomicBoolean terminal=new AtomicBoolean();
            public void onPartialResponse(String text) {if(!terminal.get())raw.append(text);}
            public void onPartialThinking(PartialThinking thinking) {if(!terminal.get())delegate.onPartialThinking(thinking);}
            public void onCompleteResponse(ChatResponse response) {
                if(!terminal.compareAndSet(false,true))return;
                if(!raw.isEmpty())delegate.onPartialResponse(ContractReviewQuality.apply(enrich(raw.toString(),sources),source,qualityEnabled));
                delegate.onCompleteResponse(response);
            }
            public void onError(Throwable error) {
                if(!terminal.compareAndSet(false,true))return;
                if(!raw.isEmpty())delegate.onPartialResponse(raw.toString());
                delegate.onError(error);
            }
        };
    }
}
