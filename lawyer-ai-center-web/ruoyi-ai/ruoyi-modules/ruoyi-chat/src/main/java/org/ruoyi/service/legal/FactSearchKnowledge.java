package org.ruoyi.service.legal;

import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.bo.vector.QueryVectorBo;
import org.ruoyi.domain.vo.knowledge.KnowledgeRetrievalVo;
import org.ruoyi.service.knowledge.*;
import org.ruoyi.service.retrieval.KnowledgeRetrievalService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.*;
import java.time.*;

/** Reuses RAG and enriches only real, readable fragments. SQL fallback is tenant scoped and parameterized. */
@Component @RequiredArgsConstructor
public class FactSearchKnowledge {
    public record Filter(String type,String status,SearchCriteria criteria) {
        public Filter(String type,String status){this(type,status,SearchCriteria.empty());}
        public Filter {type=type==null?"all":type;status=status==null?"all":status;
            if(criteria==null)criteria=SearchCriteria.empty();
            if(!List.of("all","law","case","other").contains(type)||!List.of("all","effective","amended","repealed","unknown").contains(status))throw new ServiceException("检索筛选条件无效",400);}
        public boolean accepts(KnowledgeRetrievalVo hit) {
            if(!criteria.accepts(hit))return false;
            var ref=hit.getReference();if(type.equals("other")){if(ref!=null)return false;}else if(!type.equals("all")&&(ref==null||!type.equals(ref.type())))return false;

            return status.equals("all")||(ref!=null&&"law".equals(ref.type())&&status.equals(ref.status()));
        }
    }
    public record Result(List<KnowledgeRetrievalVo> hits,int attemptedLibraries,boolean degraded,List<String> warnings) {}
    private final KnowledgeAccess access;
    private final IKnowledgeInfoService knowledge;
    private final KnowledgeRetrievalService retrieval;
    private final LegalMetadataService metadata;
    private final JdbcTemplate jdbc;
    private final ExecutorService workers=new ThreadPoolExecutor(4,4,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r->{var t=new Thread(r,"fact-retrieval");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    @jakarta.annotation.PreDestroy public void close(){workers.shutdownNow();}
    public List<Long> scope(List<Long> requested) {
        var readable=access.readableIds();
        if(requested==null||requested.isEmpty())return readable.stream().sorted().limit(4).toList();
        if(requested.size()>4||requested.stream().anyMatch(id->id==null||!readable.contains(id)))throw new ServiceException("最多选择4个可访问知识库",403);
        return requested.stream().distinct().toList();
    }
    public Result search(List<Long> ids,FactElements elements,boolean textOnly,Filter filter) {
        if(ids.isEmpty())return new Result(List.of(),0,textOnly,List.of());
        var found=new LinkedHashMap<String,KnowledgeRetrievalVo>();var warnings=new ArrayList<String>();
        boolean degraded=textOnly;var futures=new ArrayList<Future<List<KnowledgeRetrievalVo>>>();
        if(!textOnly)for(Long id:ids) {
            try {
                var info=knowledge.queryById(id);if(info==null||info.getEmbeddingModel()==null||info.getEmbeddingModel().isBlank()){degraded=true;continue;}
                var query=new QueryVectorBo();query.setKid(id.toString());query.setQuery(FactSearchRelevance.query(elements));query.setMaxResults(30);
                query.setVectorModelName(info.getVectorModel());query.setEmbeddingModelName(info.getEmbeddingModel());query.setEnableHybrid(true);
                query.setSimilarityThreshold(info.getSimilarityThreshold());query.setHybridAlpha(info.getHybridAlpha());
                query.setRetrievalPrincipal(new QueryVectorBo.RetrievalPrincipal(LoginHelper.getUserId(),LoginHelper.getTenantId()));
                futures.add(workers.submit(()->retrieval.retrieve(query)));
            } catch(RuntimeException e){degraded=true;}
        }
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(23);
        for(var future:futures)try {
            var hits=future.get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
            for(var hit:hits)if(hit.getKnowledgeId()!=null&&ids.contains(hit.getKnowledgeId()))found.putIfAbsent(hit.getKnowledgeId()+":"+hit.getId(),hit);
        }catch(Exception e){degraded=true;if(e instanceof InterruptedException)Thread.currentThread().interrupt();}finally{future.cancel(true);}
        // Also recall SQL matches before metadata filters/limit; this covers absent embedding config/indexes.
        try {for(var hit:literalOrFulltext(ids,elements,filter))found.putIfAbsent(hit.getKnowledgeId()+":"+hit.getId(),hit);}
        catch(RuntimeException e){warnings.add("关键词检索暂不可用，请稍后重试");degraded=true;}
        var candidates=metadata.enrich(new ArrayList<>(found.values())).stream().filter(filter::accepts).toList();
        var hits=FactSearchRelevance.rank(elements,candidates).stream().limit(30).toList();
        if(degraded&&!textOnly)warnings.add("部分向量检索不可用，已尝试关键词全文检索");
        return new Result(hits,ids.size(),degraded,List.copyOf(warnings));
    }
    private List<KnowledgeRetrievalVo> literalOrFulltext(List<Long> ids,FactElements elements,Filter filter) {
        var params=new ArrayList<Object>();params.add(LoginHelper.getTenantId());params.addAll(ids);
        String where=" FROM knowledge_fragment f LEFT JOIN knowledge_attach a ON a.knowledge_id=f.knowledge_id AND a.doc_id=f.doc_id AND a.tenant_id=f.tenant_id WHERE f.tenant_id=? AND f.knowledge_id IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")";
        if(filter.type().equals("case"))where+=" AND COALESCE(a.case_no,'')<>''";
        if(filter.type().equals("other"))where+=" AND COALESCE(a.case_no,'')='' AND (COALESCE(a.law_name,'')='' OR COALESCE(a.article_no,'')='')";
        if(filter.type().equals("law")||!filter.status().equals("all"))where+=" AND COALESCE(a.case_no,'')='' AND COALESCE(a.law_name,'')<>'' AND COALESCE(a.article_no,'')<>''";
        if(!filter.status().equals("all")) {
            where+=" AND (CASE WHEN a.expire_date IS NOT NULL AND a.expire_date<=? THEN 'repealed' WHEN a.validity_status IN ('有效','effective') THEN 'effective' WHEN a.validity_status IN ('已修订','已修改','amended') THEN 'amended' WHEN a.validity_status IN ('已失效','repealed') THEN 'repealed' ELSE 'unknown' END)=?";
            params.add(LocalDate.now(ZoneId.of("Asia/Shanghai")));params.add(filter.status());
        }
        String select="SELECT DISTINCT f.id,f.fid,f.doc_id,f.knowledge_id,f.idx,f.content";
        where+=filter.criteria().sql(params);
        var recalled=new LinkedHashMap<String,KnowledgeRetrievalVo>();
        var full=new ArrayList<>(params);full.add(FactSearchRelevance.query(elements));
        try {for(var hit:rows(select+where+" AND MATCH(f.content) AGAINST (? IN NATURAL LANGUAGE MODE) ORDER BY f.id DESC LIMIT 120",full))recalled.putIfAbsent(hit.getKnowledgeId()+":"+hit.getId(),hit);}
        catch(org.springframework.dao.DataAccessException unavailableIndex){/* SQL literal fallback does not require FULLTEXT. */}
        var selected=FactSearchRelevance.terms(elements);if(selected.isEmpty())return List.copyOf(recalled.values());
        var literal=new ArrayList<>(params);literal.addAll(selected);
        // Rank bounded SQL candidates by independent term coverage before truncation, not newest ID.
        // Full-text hits do not short-circuit lexical recall: broad FT terms must not hide exact matches.
        literal.addAll(selected);
        for(var hit:rows(select+where+" AND ("+String.join(" OR ",Collections.nCopies(selected.size(),"LOCATE(?,f.content)>0"))+") ORDER BY ("+String.join("+",Collections.nCopies(selected.size(),"(LOCATE(?,f.content)>0)"))+") DESC,f.id DESC LIMIT 120",literal))recalled.putIfAbsent(hit.getKnowledgeId()+":"+hit.getId(),hit);
        return List.copyOf(recalled.values());
    }
    private List<KnowledgeRetrievalVo> rows(String sql,List<Object> values) {
        return jdbc.query(sql,(r,i)->{var hit=new KnowledgeRetrievalVo();String fid=r.getString("fid");hit.setId(fid==null||fid.isBlank()?r.getString("id"):fid);hit.setDocId(r.getString("doc_id"));hit.setKnowledgeId(r.getLong("knowledge_id"));hit.setIdx(r.getInt("idx"));hit.setContent(r.getString("content"));hit.setScore(0.0);return hit;},values.toArray());
    }
}
