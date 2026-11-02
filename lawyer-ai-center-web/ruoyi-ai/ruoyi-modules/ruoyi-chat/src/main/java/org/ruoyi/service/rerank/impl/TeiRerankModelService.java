package org.ruoyi.service.rerank.impl;

import com.fasterxml.jackson.databind.JsonNode;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.domain.bo.rerank.*;
import org.ruoyi.service.embed.TeiHttpClient;
import org.ruoyi.service.rerank.RerankModelService;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Scope;
import java.util.*;

@Component("teiRerank") @Scope("prototype")
public class TeiRerankModelService implements RerankModelService {
    private ChatModelVo config;
    public void configure(ChatModelVo value){config=value;}
    public RerankResult rerank(RerankRequest request){
        if(request.getDocuments()==null||request.getDocuments().isEmpty())return RerankResult.empty();
        if(request.getQuery()==null||request.getQuery().isBlank()||request.getDocuments().size()>200)throw new ServiceException("重排输入无效或候选数超过200");
        long start=System.currentTimeMillis();
        var merged=new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode();
        // Hybrid recall can exceed the server's max_client_batch_size (32 here).
        // Cross-encoder scores are independent per query/document pair, so batch without changing rank semantics.
        for(int offset=0;offset<request.getDocuments().size();offset+=16){
            var batch=request.getDocuments().subList(offset,Math.min(offset+16,request.getDocuments().size()));
            var response=TeiHttpClient.post(config,"/rerank",Map.of("query",request.getQuery(),"texts",batch,"truncate",true,"raw_scores",false,"return_text",false));
            var partial=RerankRequest.builder().documents(batch).topN(batch.size()).build();
            parse(response,partial,0); // Validate local indices before mapping to the original candidate list.
            for(var row:response){var adjusted=(com.fasterxml.jackson.databind.node.ObjectNode)row.deepCopy();adjusted.put("index",row.path("index").intValue()+offset);merged.add(adjusted);}
        }
        return parse(merged,request,System.currentTimeMillis()-start);
    }
    static RerankResult parse(JsonNode response,RerankRequest request,long elapsed){
        if(response==null||!response.isArray()||response.size()!=request.getDocuments().size())throw new ServiceException("TEI重排数量不匹配");
        List<RerankResult.RerankDocument> rows=new ArrayList<>();Set<Integer> seen=new HashSet<>();
        for(var row:response){
            var index=row.get("index");var score=row.get("score");
            if(index==null||!index.isIntegralNumber()||!index.canConvertToInt()||index.intValue()<0||index.intValue()>=request.getDocuments().size()||!seen.add(index.intValue())||score==null||!score.isNumber()||!Double.isFinite(score.doubleValue()))throw new ServiceException("TEI重排结果格式无效");
            rows.add(RerankResult.RerankDocument.builder().index(index.intValue()).relevanceScore(score.doubleValue()).document(Boolean.TRUE.equals(request.getReturnDocuments())?request.getDocuments().get(index.intValue()):null).build());
        }
        rows.sort(Comparator.comparingDouble(RerankResult.RerankDocument::getRelevanceScore).reversed());
        int limit=request.getTopN()==null?rows.size():Math.max(0,Math.min(request.getTopN(),rows.size()));
        return RerankResult.builder().documents(List.copyOf(rows.subList(0,limit))).totalDocuments(request.getDocuments().size()).durationMs(elapsed).build();
    }
}
