package org.ruoyi.service.embed.impl;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.enums.ModalityType;
import org.ruoyi.service.embed.*;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Scope;
import java.util.*;

/** Native Hugging Face TEI /embed, compatible with the user's local Qwen embedding service. */
@Component("tei") @Scope("prototype")
public class TeiEmbeddingProvider implements BaseEmbedModelService {
    private ChatModelVo config;
    public void configure(ChatModelVo value){config=value;}
    public Set<ModalityType> getSupportedModalities(){return Set.of(ModalityType.TEXT);}
    public Response<List<Embedding>> embedAll(List<TextSegment> segments){
        if(segments==null||segments.isEmpty())return Response.from(List.of());
        List<Embedding> results=new ArrayList<>();
        // Small batches accommodate GPU memory without cutting a legal provision into fragments.
        for(int start=0;start<segments.size();start+=16){
            var input=segments.subList(start,Math.min(start+16,segments.size())).stream().map(TextSegment::text).toList();
            if(input.stream().anyMatch(s->s==null||s.isBlank()))throw new ServiceException("Embedding文本不能为空");
            var response=TeiHttpClient.post(config,"/embed",Map.of("inputs",input,"normalize",true,"truncate",false));
            results.addAll(parse(response,input.size(),config.getModelDimension()));
        }
        return Response.from(results);
    }
    static List<Embedding> parse(JsonNode node,int expected,Integer dimension){
        if(node==null||!node.isArray()||node.size()!=expected)throw new ServiceException("TEI向量数量不匹配").setDetailMessage("TEI_VECTOR_COUNT_MISMATCH");
        List<Embedding> result=new ArrayList<>();
        for(var row:node){
            if(!row.isArray()||row.isEmpty()||dimension!=null&&dimension>0&&row.size()!=dimension)throw new ServiceException("TEI向量维度不匹配").setDetailMessage("TEI_DIMENSION_MISMATCH");
            float[] vector=new float[row.size()];double norm=0;
            for(int i=0;i<vector.length;i++){
                if(!row.get(i).isNumber())throw new ServiceException("TEI向量格式无效").setDetailMessage("TEI_INVALID_VECTOR");
                vector[i]=row.get(i).floatValue();if(!Float.isFinite(vector[i]))throw new ServiceException("TEI向量含非有限数值").setDetailMessage("TEI_INVALID_VECTOR");norm+=(double)vector[i]*vector[i];
            }
            if(norm==0)throw new ServiceException("TEI返回零向量").setDetailMessage("TEI_ZERO_VECTOR");
            result.add(Embedding.from(vector));
        }
        return result;
    }
}
