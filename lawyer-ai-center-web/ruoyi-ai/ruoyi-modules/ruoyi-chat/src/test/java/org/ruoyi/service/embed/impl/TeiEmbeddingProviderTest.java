package org.ruoyi.service.embed.impl;
import org.junit.jupiter.api.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
@Tag("dev")
class TeiEmbeddingProviderTest {
    private final ObjectMapper json=new ObjectMapper();
    @Test void classifiesNullOverflowAndZeroVectorsInsteadOfAcceptingHttpSuccess()throws Exception{
        for(String payload:java.util.List.of("[[0,null,0,0]]","[[1e300,0,0,0]]")){
            var failure=assertThrows(org.ruoyi.common.core.exception.ServiceException.class,()->TeiEmbeddingProvider.parse(json.readTree(payload),1,4));
            assertEquals("TEI_INVALID_VECTOR",failure.getDetailMessage());
            assertEquals("TEI_INVALID_VECTOR",org.ruoyi.service.retrieval.ModelServiceFailureSummary.category(new java.util.concurrent.CompletionException(failure)));
        }
        var zero=assertThrows(org.ruoyi.common.core.exception.ServiceException.class,()->TeiEmbeddingProvider.parse(json.readTree("[[0,0,0,0]]"),1,4));
        assertEquals("TEI_ZERO_VECTOR",zero.getDetailMessage());
    }
    @Test void acceptsDenseVectorsInInputOrder()throws Exception{
        var result=TeiEmbeddingProvider.parse(json.readTree("[[1,0],[0,1]]"),2,2);
        assertArrayEquals(new float[]{1,0},result.get(0).vector());assertArrayEquals(new float[]{0,1},result.get(1).vector());
    }
    @Test void rejectsMissingOrWrongDimensionOrInvalidNumbers()throws Exception{
        assertThrows(RuntimeException.class,()->TeiEmbeddingProvider.parse(json.readTree("[[1,0]]"),2,2));
        assertThrows(RuntimeException.class,()->TeiEmbeddingProvider.parse(json.readTree("[[1,0]]"),1,3));
        assertThrows(RuntimeException.class,()->TeiEmbeddingProvider.parse(json.readTree("[[0,0]]"),1,2));
        assertThrows(RuntimeException.class,()->TeiEmbeddingProvider.parse(json.readTree("[[1,\"x\"]]"),1,2));
    }
}
