package org.ruoyi.service.legal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.response.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.*;

/** The uploaded document supplies structure; this request's elements supply facts. */
public final class LitigationDrafting {
    private LitigationDrafting() {}
    public static final int MAX_FACTS_LENGTH = 20_000;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern INPUT = Pattern.compile("---要素JSON开始---\\s*([\\s\\S]*?)\\s*---要素JSON结束---");
    private static final Pattern SLOT = Pattern.compile("\\{\\{([^{}]{1,100})}}");
    private static final Pattern CITATIONS = Pattern.compile("```(?:json|legal-citations)\\s*\\r?\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADING = Pattern.compile("(?m)^[\\t ]*(?:#{1,6}[\\t ]*)?(?:\\*\\*)?(?:[一二三四五六七八九十]+[、.．][\\t ]*)?(诉讼请求|答辩意见|答辩请求|事实与理由|事实和理由|证据与来源|证据清单|证据|此致|具状人|答辩人|日期|原告|被告)(?=[：:\\s*]|$)[：:]?(?:\\*\\*)?[\\t ]*\\\\?");
    public static final String SYSTEM_PROMPT = """
        【本次诉讼文书交付规则，优先于通用聊天要求】
        本任务只输出完整的民事起诉状或民事答辩状正文，使用Markdown；不解释、不追问、不附注释、不输出代码围栏。
        本次要素JSON中的原告、被告、请求、事实、证据、法院是唯一的案件事实来源。必须逐项使用已填写内容；不得将已填写的诉讼请求、事实与理由或法院留空、写成待补充或返回模板字段名。
        将简略请求结合已给事实改写为正式表述，但不得增加用户未提出的金额、利息、费用、责任、程序经过或其他请求。事实可以整理表达，不能省略关键事实，不能将争议事实认定为已查明。
        上传模板仅提供同类文书的章节与措辞，不提供本案人物、金额、日期或事实。所有模板字段必须填入本次要素；严禁输出双花括号占位符。只有用户确实未提供的个别信息才标【具体字段待补充】；未填日期标【具状日期待填写】。
        必须包含当事人、诉讼请求/答辩意见、事实与理由、证据、法院与落款，不能只返回空表。
        检索资料只用于核对法律，不得覆盖本次事实。只有本次检索原文支持的法条才可在正文中自然引用；无支持时不写具体条号。
        正文内外都禁止输出引用元数据、JSON数组、legal-citations、fragmentId、sourceUrl、知识库命中说明或机器字段；不能把通用聊天的引用输出格式带入文书。
        """;

    public record Input(String type, String plaintiff, String defendant, String claims, String facts, String evidence, String court) {}
    public static boolean applies(String content) { return content != null && INPUT.matcher(content).find(); }
    public static Input input(String content) {
        var match = INPUT.matcher(Objects.toString(content, ""));
        if (!match.find()) return null;
        try {
            JsonNode row = JSON.readTree(match.group(1));
            String type = text(row, "type");
            if (!Set.of("民事起诉状", "民事答辩状").contains(type)) throw new IllegalArgumentException();
            for (String field : List.of("plaintiff", "defendant", "claims", "facts")) {
                String value = text(row, field);
                if (value.isBlank() || SLOT.matcher(value).find()) throw new IllegalArgumentException();
            }
            if (row.path("facts").asText("").length() > MAX_FACTS_LENGTH) {
                throw new DraftingException("事实与理由超过20,000字长度限制，请缩减后重试", 400);
            }
            return new Input(type, text(row,"plaintiff"), text(row,"defendant"), text(row,"claims"), text(row,"facts"), text(row,"evidence"), text(row,"court"));
        } catch (DraftingException invalid) { throw invalid; }
        catch (Exception invalid) { throw new DraftingException("请填写真实的双方当事人、请求及事实理由，不要直接提交模板占位符", 400); }
    }
    private static String text(JsonNode row, String field) { return row.path(field).isTextual() ? row.path(field).asText().strip() : ""; }

    public static String compatibilityError(String type, String template) {
        return TemplateTypeCompatibility.error(type,null,template);
    }
    public static void requireCompatible(String type, String template) {
        String error = compatibilityError(type, template);
        if (!error.isEmpty()) throw new DraftingException(error, 422);
    }

    public static String fill(String template, Input input) {
        if (input == null) return template;
        Map<String,String> values = new HashMap<>();
        for (String key : List.of("原告", "原告资料", "原告信息", "plaintiff")) values.put(key, input.plaintiff());
        for (String key : List.of("被告", "被告资料", "被告信息", "defendant")) values.put(key, input.defendant());
        for (String key : List.of("诉讼请求", "答辩意见", "答辩请求", "请求", "claims")) values.put(key, input.claims());
        for (String key : List.of("事实与理由", "事实和理由", "事实理由", "facts")) values.put(key, input.facts());
        for (String key : List.of("证据", "证据清单", "证据与来源", "evidence")) values.put(key, input.evidence().isBlank() ? "【证据及来源待补充】" : input.evidence());
        for (String key : List.of("法院", "受理法院", "受理法院待确认", "court")) values.put(key, input.court().isBlank() ? "【受理法院待确认】" : input.court());
        String signer = ("民事答辩状".equals(input.type()) ? input.defendant() : input.plaintiff()).split("[，,；;\\n]", 2)[0];
        for (String key : List.of("具状人", "签名", "答辩人")) values.put(key, signer);
        for (String key : List.of("日期", "具状日期", "待填写")) values.put(key, "【具状日期待填写】");
        var matcher = SLOT.matcher(template); var output = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1).replaceAll("[\\s：:]", "");
            matcher.appendReplacement(output, Matcher.quoteReplacement(values.getOrDefault(key, "【" + key + "待补充】")));
        }
        matcher.appendTail(output); return output.toString();
    }
    public static String cleanDocument(String raw) { return DocumentCitations.clean(raw); }
    public static String finish(String raw, String request) {
        Input input = input(request);
        if (input == null) return cleanDocument(raw);
        String document = fill(cleanDocument(raw), input);
        requireCompatible(input.type(), document);
        document = retainProvidedFactsAndRequests(document, input);
        Map<String,String> sections = new HashMap<>(); var matcher = HEADING.matcher(document);
        String heading = null; int start = 0;
        while (matcher.find()) {
            if (heading != null) sections.put(heading, document.substring(start, matcher.start()).strip());
            heading = matcher.group(1); start = matcher.end();
        }
        if (heading != null) sections.put(heading, document.substring(start).strip());
        String claims = sections.getOrDefault("民事起诉状".equals(input.type()) ? "诉讼请求" : "答辩意见", sections.getOrDefault("答辩请求", ""));
        String facts = sections.getOrDefault("事实与理由", sections.getOrDefault("事实和理由", ""));
        if (claims.isBlank() || facts.isBlank() || claims.matches("[\\s*\\\\:：]*【[^】]*待[^】]*】[\\s*\\\\]*") || facts.matches("[\\s*\\\\:：]*【[^】]*待[^】]*】[\\s*\\\\]*") || SLOT.matcher(document).find() || Pattern.compile("(?i)(fragmentId|sourceUrl)\\s*[\"']?\\s*:").matcher(document).find()) {
            throw new DraftingException("起草结果未完整填入诉讼请求或事实理由，请重新生成或补充要素；未将空模板作为成稿保存", 422);
        }
        return document;
    }
    private record Section(int start, int end, String content) {}
    private static Section section(String document, Set<String> names) {
        var matcher = HEADING.matcher(document); int start = -1;
        while (matcher.find()) {
            if (start >= 0) return new Section(start, matcher.start(), document.substring(start, matcher.start()).strip());
            if (names.contains(matcher.group(1))) start = matcher.end();
        }
        return start < 0 ? null : new Section(start, document.length(), document.substring(start).strip());
    }
    private static String replaceSection(String document, Section section, String content) {
        return document.substring(0,section.start())+"\n"+content.strip()+"\n\n"+document.substring(section.end());
    }
    /** Do not let stock legal prose add unsupported events or unrequested heads of claim. */
    private static String retainProvidedFactsAndRequests(String document, Input input) {
        var facts = section(document, Set.of("事实与理由", "事实和理由"));
        String source = input.facts()+"\n"+input.evidence()+"\n"+input.claims();
        if (facts != null) {
            for (String phrase : List.of("多次", "开庭", "庭审", "微信", "公证", "资金周转", "银行", "予以确认", "恶意", "拒不")) {
                var denied = Pattern.compile("(?:未提供|没有|未曾|无).{0,12}"+Pattern.quote(phrase));
                if (facts.content().contains(phrase) && (!source.contains(phrase) || denied.matcher(source).find()) && !denied.matcher(facts.content()).find()) {
                    document = replaceSection(document, facts, input.facts()); break;
                }
            }
        }
        var claims = section(document, Set.of("诉讼请求", "答辩意见", "答辩请求"));
        if (claims != null && "民事起诉状".equals(input.type())) {
            List<String> allowed = new ArrayList<>(); boolean removed = false;
            for (String line : claims.content().split("\\n")) {
                boolean unsupported = false;
                for (String extra : List.of("诉讼费", "律师费", "保全费", "鉴定费", "公告费", "利息", "违约金", "赔偿金")) {
                    if (line.contains(extra) && !input.claims().contains(extra)) { unsupported = true; break; }
                }
                if (unsupported) removed = true; else allowed.add(line);
            }
            if (removed) {
                String retained = String.join("\n", allowed).strip();
                document = replaceSection(document, claims, retained.isBlank() ? input.claims() : retained);
            }
        }
        if (!input.evidence().isBlank()) {
            String beforeEvidence = document;
            boolean missing = Arrays.stream(input.evidence().split("[、，,；;\\n]+"))
                .map(String::strip).filter(value -> !value.isEmpty()).anyMatch(value -> !documentContains(beforeEvidence, value));
            if (missing) {
                var evidence = section(document, Set.of("证据", "证据与来源", "证据清单"));
                if (evidence != null) document = replaceSection(document, evidence, input.evidence());
                else {
                    var court = HEADING.matcher(document); int position = document.length();
                    while (court.find()) if ("此致".equals(court.group(1))) { position = court.start(); break; }
                    document = document.substring(0,position)+"## 证据与来源\n\n"+input.evidence()+"\n\n"+document.substring(position);
                }
            }
        }
        return document;
    }
    private static boolean documentContains(String document, String value) { return document.replaceAll("\\s", "").contains(value.replaceAll("\\s", "")); }
    /** Buffer the draft so preview and stored history receive the same checked document. */
    public static StreamingChatResponseHandler wrap(StreamingChatResponseHandler delegate, String request) {
        return new StreamingChatResponseHandler() {
            private final StringBuilder raw = new StringBuilder();
            private final AtomicBoolean terminal = new AtomicBoolean();
            public void onPartialResponse(String text) { if (!terminal.get()) raw.append(text); }
            public void onPartialThinking(PartialThinking thinking) { if (!terminal.get()) delegate.onPartialThinking(thinking); }
            public void onCompleteResponse(ChatResponse response) {
                if (!terminal.compareAndSet(false, true)) return;
                try {
                    String text = raw.isEmpty() && response != null && response.aiMessage() != null ? response.aiMessage().text() : raw.toString();
                    delegate.onPartialResponse(finish(text, request)); delegate.onCompleteResponse(response);
                } catch (DraftingException error) { delegate.onError(error); }
            }
            public void onError(Throwable error) { if (terminal.compareAndSet(false, true)) delegate.onError(error); }
        };
    }
}
