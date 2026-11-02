package org.ruoyi.service.knowledge;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ruoyi.service.knowledge.retriever.BoundedCompositeContentRetriever;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class BoundedCompositeContentRetrieverTest {
    private Content source(String kid, String id, String text) {
        var metadata = new Metadata();
        metadata.put("kid", kid); metadata.put("docId", id); metadata.put("fid", id);
        return Content.from(TextSegment.from(text, metadata));
    }

    private Content scored(String kid, String id, String model, double score) {
        Content content = source(kid, id, "原文" + id);
        content.textSegment().metadata().put("rerank_model", model);
        content.textSegment().metadata().put("rerank_score", String.valueOf(score));
        return content;
    }

    @Test void sharedRerankerKeepsLowerRankedDirectEvidenceInsteadOfEqualQuotasForIrrelevantLibraries() {
        var first = List.of(scored("civil", "a", "shared", .99), scored("civil", "b", "shared", .98), scored("civil", "c", "shared", .97));
        var second = List.of(scored("other", "d", "shared", .1), scored("other", "e", "shared", .09));
        var retriever = new BoundedCompositeContentRetriever(List.of(q -> first, q -> second), 4, 24000);
        assertEquals(List.of(first.get(0), second.get(0), first.get(1), first.get(2)), retriever.retrieve(Query.from("问题")));
    }

    @Test void differentRerankModelsDoNotCompareScoresAcrossLibraries() {
        var first = List.of(scored("civil", "a", "model-a", .9), scored("civil", "b", "model-a", .8));
        var second = List.of(scored("other", "c", "model-b", .99), scored("other", "d", "model-b", .98));
        var retriever = new BoundedCompositeContentRetriever(List.of(q -> first, q -> second), 3, 24000);
        assertEquals(List.of(first.get(0), second.get(0), first.get(1)), retriever.retrieve(Query.from("问题")));
    }

    @Test void laterLibrariesRetainTheirHighestRankedEvidenceWhenEarlierLibraryFillsBudget() {
        var early = IntStream.range(0, 20).mapToObj(i -> source("first", "" + i, "合同规则" + i)).toList();
        Content exception = source("later", "7", "劳动法定例外原文");
        var retriever = new BoundedCompositeContentRetriever(List.of(q -> early, q -> List.of(exception)), 20, 24000);
        var selected = retriever.retrieve(Query.from("问题"));
        assertEquals(20, selected.size());
        assertSame(exception, selected.get(1));
        assertSame(early.get(18), selected.get(19));
    }

    @Test void anOversizedFragmentDoesNotSuppressShorterCompleteEvidenceThatStillFits() {
        Content small = source("first", "2", "完整法条");
        Content other = source("later", "1", "例外");
        var retriever = new BoundedCompositeContentRetriever(List.of(
            q -> List.of(source("first", "1", "长".repeat(30)), small), q -> List.of(other)), 20, 10);
        var selected = retriever.retrieve(Query.from("问题"));
        assertEquals(List.of(other, small), selected);
        assertTrue(selected.stream().mapToInt(c -> c.textSegment().text().length()).sum() <= 10);
    }

    @Test void sourceIdentityDeduplicatesRepeatedFragmentsWithoutMergingDifferentLibrariesOrUnidentifiedTexts() {
        Content first = source("one", "same", "相同正文");
        Content differentLibrary = source("two", "same", "相同正文");
        var unidentifiedA = Content.from(TextSegment.from("无标识甲"));
        var unidentifiedB = Content.from(TextSegment.from("无标识乙"));
        var retriever = new BoundedCompositeContentRetriever(List.of(
            q -> List.of(first, unidentifiedA),
            q -> List.of(first, differentLibrary, unidentifiedA, unidentifiedB)));
        assertEquals(List.of(first, unidentifiedA, differentLibrary, unidentifiedB), retriever.retrieve(Query.from("问题")));
    }

    @Test void oneUnavailableLibraryDoesNotRemoveEvidenceFromHealthyLibraries() {
        Content evidence = source("healthy", "1", "原文");
        ContentRetriever failed = q -> { throw new IllegalStateException("not logged"); };
        var retriever = new BoundedCompositeContentRetriever(List.of(failed, q -> null, q -> List.of(evidence)));
        assertEquals(List.of(evidence), retriever.retrieve(Query.from("问题")));
    }
}
