package org.ruoyi.service.rerank.impl;
import org.junit.jupiter.api.*;
import org.ruoyi.domain.bo.rerank.RerankRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
@Tag("dev")
class TeiRerankModelServiceTest {
    private final ObjectMapper json=new ObjectMapper();
    private RerankRequest request(){return RerankRequest.builder().query("付款").documents(List.of("文档A","文档B")).topN(1).returnDocuments(true).build();}
    @Test void hybridCandidatesAreBatchedAndGlobalIndicesPreserved(){
        var documents=java.util.stream.IntStream.range(0,36).mapToObj(i->"文档"+i).toList();
        try(var transport=org.mockito.Mockito.mockStatic(org.ruoyi.service.embed.TeiHttpClient.class)){
            transport.when(()->org.ruoyi.service.embed.TeiHttpClient.post(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq("/rerank"),org.mockito.ArgumentMatchers.any())).thenAnswer(call->{
                java.util.Map<?,?> payload=call.getArgument(2);var texts=(List<?>)payload.get("texts");assertTrue(texts.size()<=16);
                var response=json.createArrayNode();for(int i=0;i<texts.size();i++)response.addObject().put("index",i).put("score",Integer.parseInt(texts.get(i).toString().substring(2))/100.0);return response;
            });
            var service=new TeiRerankModelService();service.configure(new org.ruoyi.common.chat.domain.vo.chat.ChatModelVo());
            var result=service.rerank(RerankRequest.builder().query("付款").documents(documents).topN(3).returnDocuments(true).build());
            assertEquals(List.of(35,34,33),result.getDocuments().stream().map(x->x.getIndex()).toList());
            assertEquals("文档35",result.getDocuments().get(0).getDocument());
            transport.verify(()->org.ruoyi.service.embed.TeiHttpClient.post(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq("/rerank"),org.mockito.ArgumentMatchers.any()),org.mockito.Mockito.times(3));
        }
    }
    @Test void mapsIndicesSortsScoresAndAppliesTopN()throws Exception{
        var result=TeiRerankModelService.parse(json.readTree("[{\"index\":0,\"score\":0.1},{\"index\":1,\"score\":0.9}]"),request(),4);
        assertEquals(1,result.getDocuments().size());assertEquals(1,result.getDocuments().get(0).getIndex());assertEquals("文档B",result.getDocuments().get(0).getDocument());assertEquals(2,result.getTotalDocuments());
    }
    @Test void rejectsDuplicatedMissingAndOutOfBoundsIndices()throws Exception{
        for(String payload:List.of("[]","[{\"index\":0,\"score\":0.1},{\"index\":0,\"score\":0.9}]","[{\"index\":0,\"score\":0.1},{\"index\":2,\"score\":0.9}]"))assertThrows(RuntimeException.class,()->TeiRerankModelService.parse(json.readTree(payload),request(),0));
    }
}
