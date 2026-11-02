package org.ruoyi.service.knowledge;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.service.retrieval.ModelServiceFailureSummary;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class ModelServiceFailureSummaryTest {
    @Test void wrappedHttpStatusRemainsVisibleWithoutPrivateBody() {
        var failure=new ServiceException("private contract and token").setDetailMessage("TEI_HTTP_429");
        assertEquals("TEI_HTTP_429",ModelServiceFailureSummary.category(new CompletionException(failure)));
    }
    @Test void distinguishesWholeVectorDeadlineFromTeiTransportDeadline() {
        assertEquals("VECTOR_TIMEOUT",ModelServiceFailureSummary.category(new CompletionException(new TimeoutException("private"))));
        assertEquals("TEI_TIMEOUT",ModelServiceFailureSummary.category(new ServiceException("private").setDetailMessage("TEI_TIMEOUT")));
    }
    @Test void untrustedMessagesAndDetailsNeverBecomeLogSummaries() {
        var failure=new ServiceException("Authorization: secret").setDetailMessage("TEI_HTTP_500\ncontract body");
        assertEquals("ServiceException",ModelServiceFailureSummary.category(new CompletionException(failure)));
        assertEquals("IllegalStateException",ModelServiceFailureSummary.category(new IllegalStateException("private")));
    }
}
