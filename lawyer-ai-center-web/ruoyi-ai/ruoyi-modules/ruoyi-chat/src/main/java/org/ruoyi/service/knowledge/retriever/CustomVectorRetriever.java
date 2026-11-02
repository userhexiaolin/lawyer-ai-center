package org.ruoyi.service.knowledge.retriever;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.domain.bo.vector.QueryVectorBo;
import org.ruoyi.domain.vo.knowledge.KnowledgeInfoVo;
import org.ruoyi.service.retrieval.KnowledgeRetrievalService;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 自定义检索器：适配 LangChain4j ContentRetriever 接口
 * 桥接统一的 KnowledgeRetrievalService，支持配置化的混合检索、阈值过滤等功能
 *
 * @author RobustH
 */
@Slf4j
public class CustomVectorRetriever implements ContentRetriever {

    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final KnowledgeInfoVo knowledgeInfoVo;
    private final ChatModelVo chatModelVo;
    private final QueryVectorBo.RetrievalPrincipal principal;
    public CustomVectorRetriever(KnowledgeRetrievalService service,KnowledgeInfoVo info,ChatModelVo model){
        this(service,info,model,new QueryVectorBo.RetrievalPrincipal(org.ruoyi.common.satoken.utils.LoginHelper.getUserId(),org.ruoyi.common.satoken.utils.LoginHelper.getTenantId()));
    }
    public CustomVectorRetriever(KnowledgeRetrievalService service,KnowledgeInfoVo info,ChatModelVo model,QueryVectorBo.RetrievalPrincipal principal){
        this.knowledgeRetrievalService=service;this.knowledgeInfoVo=info;this.chatModelVo=model;this.principal=principal;
    }

    @Override
    public List<Content> retrieve(Query query) {
        log.info("执行自定义检索，queryChars={}", query.text().length());

        // 构建增强后的查询参数
        QueryVectorBo queryVectorBo = new QueryVectorBo();
        queryVectorBo.setRetrievalPrincipal(principal);
        queryVectorBo.setQuery(query.text());
        queryVectorBo.setKid(String.valueOf(knowledgeInfoVo.getId()));
        queryVectorBo.setBaseUrl(chatModelVo.getApiHost());
        queryVectorBo.setVectorModelName(knowledgeInfoVo.getVectorModel());
        queryVectorBo.setEmbeddingModelName(knowledgeInfoVo.getEmbeddingModel());
        
        // 应用知识库配置参数
        queryVectorBo.setMaxResults(knowledgeInfoVo.getRetrieveLimit());
        queryVectorBo.setSimilarityThreshold(knowledgeInfoVo.getSimilarityThreshold());
        queryVectorBo.setEnableHybrid(Objects.equals(knowledgeInfoVo.getEnableHybrid(), 1));
        queryVectorBo.setHybridAlpha(knowledgeInfoVo.getHybridAlpha());

        // 设置重排序参数 (如果 retriever 阶段也想做初步重排，可以在此设置)
        queryVectorBo.setEnableRerank(Objects.equals(knowledgeInfoVo.getEnableRerank(), 1));
        queryVectorBo.setRerankModelName(knowledgeInfoVo.getRerankModel());
        queryVectorBo.setRerankTopN(knowledgeInfoVo.getRerankTopN());
        queryVectorBo.setRerankScoreThreshold(knowledgeInfoVo.getRerankScoreThreshold());

        // 通过统一服务执行检索
        var nearestList = knowledgeRetrievalService.retrieve(queryVectorBo);

        // 将结果包装为标准的 Content 返回
        return nearestList.stream()
                .map(vo -> {
                    Metadata metadata = new Metadata();
                    metadata.put("kid", String.valueOf(knowledgeInfoVo.getId()));
                    metadata.put("docId", Objects.toString(vo.getDocId(), ""));
                    metadata.put("fid", Objects.toString(vo.getId(), ""));
                    // Only successful reranking produces rawScore. Do not label coarse fallback scores as comparable.
                    if (vo.getRawScore() != null && Double.isFinite(vo.getRawScore())
                        && vo.getScore() != null && Double.isFinite(vo.getScore())
                        && vo.getScore() >= 0 && vo.getScore() <= 1
                        && knowledgeInfoVo.getRerankModel() != null && !knowledgeInfoVo.getRerankModel().isBlank()) {
                        metadata.put("rerank_model", knowledgeInfoVo.getRerankModel());
                        metadata.put("rerank_score", String.valueOf(vo.getScore()));
                    }
                    String citation = vo.getCitation();
                    if (!citation.isBlank()) metadata.put("citation", citation);
                    if (vo.getLegalMetadata() != null) {
                        var legal = vo.getLegalMetadata();
                        metadata.put("law_name", Objects.toString(legal.lawName(), ""));
                        metadata.put("article_no", Objects.toString(legal.articleNo(), ""));
                        metadata.put("region", Objects.toString(legal.region(), ""));
                        metadata.put("validity_status", Objects.toString(legal.validityStatus(), ""));
                        metadata.put("publish_date", Objects.toString(legal.publishDate(), ""));
                        metadata.put("expire_date", Objects.toString(legal.expireDate(), ""));
                        metadata.put("source_version", Objects.toString(legal.sourceVersion(), ""));
                    }
                    var reference = vo.getReference();
                    String structured = "";
                    if (reference != null) {
                        metadata.put("law_name", Objects.toString(reference.lawName(), ""));
                        metadata.put("article_no", Objects.toString(reference.articleNo(), ""));
                        structured = "引用元数据（请按约定输出，禁止改写编号）：" + reference.toJson() + "\n";
                        metadata.put("status", Objects.toString(reference.status(), ""));
                        metadata.put("case_no", Objects.toString(reference.caseNo(), ""));
                        metadata.put("source_url", Objects.toString(reference.sourceUrl(), ""));
                        metadata.put("fragmentId", Objects.toString(reference.fragmentId(), ""));
                    }
                    String content = citation.isBlank() ? vo.getContent() : "来源：" + citation + "。回答引用时请保留法规名、条款及此时效状态。\n" + vo.getContent();
                    return Content.from(TextSegment.from(structured + content, metadata));
                })
                .collect(Collectors.toList());
    }
}
