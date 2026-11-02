package org.ruoyi.service.knowledge.retriever;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;

/** Merges per-library rankings without letting the first library exhaust the context budget. */
@Slf4j
public final class BoundedCompositeContentRetriever implements ContentRetriever {
    private final List<ContentRetriever> delegates;
    private final int maxSegments;
    private final int maxChars;

    public BoundedCompositeContentRetriever(List<ContentRetriever> delegates) {
        this(delegates, 20, 24000);
    }

    public BoundedCompositeContentRetriever(List<ContentRetriever> delegates, int maxSegments, int maxChars) {
        if (maxSegments < 1 || maxChars < 1) throw new IllegalArgumentException("Positive context budget required");
        this.delegates = List.copyOf(delegates);
        this.maxSegments = maxSegments;
        this.maxChars = maxChars;
    }

    @Override
    public List<Content> retrieve(Query query) {
        List<CompletableFuture<List<Content>>> futures = delegates.stream().map(delegate ->
            CompletableFuture.supplyAsync(() -> {
                try {
                    List<Content> result = delegate.retrieve(query);
                    return result == null ? List.<Content>of() : result;
                } catch (Exception error) {
                    log.warn("knowledge_retriever operation=RETRIEVE status=FAILED errorType={}", error.getClass().getSimpleName());
                    return List.<Content>of();
                }
            })).toList();
        List<List<Content>> rankings = futures.stream().map(CompletableFuture::join).toList();
        List<Content> selected = new ArrayList<>();
        Set<SegmentIdentity> seen = new HashSet<>();
        int chars = 0;
        int rounds = rankings.stream().mapToInt(List::size).max().orElse(0);
        List<Content> ordered = new ArrayList<>();
        // Keep the best evidence from each library, then spend the remaining budget by comparable relevance.
        // Scores from different models or an unranked fallback must not be compared across libraries.
        List<Content> all = rankings.stream().flatMap(List::stream).toList();
        boolean comparable = !all.isEmpty() && all.stream().allMatch(this::hasRerankScore)
            && all.stream().map(c -> c.textSegment().metadata().getString("rerank_model")).distinct().count() == 1;
        for (int rank = 0; rank < rounds; rank++) {
            for (List<Content> ranking : rankings) {
                if (rank >= ranking.size()) continue;
                if (!comparable || rank == 0) ordered.add(ranking.get(rank));
            }
        }
        if (comparable) {
            rankings.stream().flatMap(ranking -> ranking.stream().skip(1))
                .sorted(Comparator.comparingDouble(this::rerankScore).reversed()).forEach(ordered::add);
        }
        for (Content content : ordered) {
            String text = content.textSegment().text();
            if (text.isBlank() || text.length() > maxChars - chars) continue;
            if (!seen.add(identity(content))) continue;
            selected.add(content);
            chars += text.length();
            if (selected.size() == maxSegments) break;
        }
        log.info("knowledge_context libraries={} segments={} chars={} allocation={}", delegates.size(), selected.size(), chars,
            comparable ? "RERANK_RELEVANCE" : "PER_LIBRARY_RANK");
        return selected;
    }

    private boolean hasRerankScore(Content content) {
        String model = content.textSegment().metadata().getString("rerank_model");
        if (model == null || model.isBlank()) return false;
        try { double score = rerankScore(content); return Double.isFinite(score) && score >= 0 && score <= 1; }
        catch (NumberFormatException | NullPointerException error) { return false; }
    }

    private double rerankScore(Content content) {
        return Double.parseDouble(content.textSegment().metadata().getString("rerank_score"));
    }

    private SegmentIdentity identity(Content content) {
        var metadata = content.textSegment().metadata();
        String kid = Objects.toString(metadata.getString("kid"), "");
        String docId = Objects.toString(metadata.getString("docId"), "");
        String fid = Objects.toString(metadata.getString("fid"), "");
        return new SegmentIdentity(kid, docId, fid,
            kid.isBlank() && docId.isBlank() && fid.isBlank() ? content.textSegment().text() : "");
    }

    private record SegmentIdentity(String kid, String docId, String fid, String fallbackText) { }
}
