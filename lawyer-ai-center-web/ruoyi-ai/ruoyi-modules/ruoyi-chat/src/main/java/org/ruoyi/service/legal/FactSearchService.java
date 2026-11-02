package org.ruoyi.service.legal;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.domain.vo.knowledge.*;
import org.ruoyi.service.chat.*;
import org.ruoyi.service.knowledge.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service @RequiredArgsConstructor
public class FactSearchService {
    public record Request(Long sessionId,String text,List<Long> knowledgeIds,String type,String status,String keyword,String dateFrom,String dateTo,String region,String topic) {
        public Request(Long sessionId,String text,List<Long> knowledgeIds,String type,String status){this(sessionId,text,knowledgeIds,type,status,null,null,null,null,null);}
    }
    public record Library(Long id,String name) {}
    public record Hit(String id,Long knowledgeId,String sourceName,String content,boolean truncated,LegalReference reference,LegalMetadataService.LegalMetadata legalMetadata) {}
    public record Result(Long sessionId,FactElements elements,String extractionStatus,String retrievalMode,String query,int attemptedLibraries,List<Hit> hits,List<String> warnings,String message) {}
    private final FactSearchModel model;
    private final FactSearchKnowledge knowledge;
    private final KnowledgeAccess access;
    private final IKnowledgeInfoService info;
    private final ChatSessionOwnershipGuard ownership;
    private final IChatSessionService sessions;
    private final LawyerUsageService usage;
    private final IChatMessageService messages;
    public List<Library> libraries() {
        return access.readableIds().stream().sorted().map(id->{var item=info.queryById(id);return new Library(id,item==null?"知识库 "+id:item.getName());}).toList();
    }
    public Result search(Request request) {
        if(request==null||request.text()==null||request.text().isBlank()||request.text().length()>12000)throw new ServiceException("请输入不超过12000字的案情",400);
        var filter=new FactSearchKnowledge.Filter(request.type(),request.status(),new SearchCriteria(request.keyword(),request.dateFrom(),request.dateTo(),request.region(),request.topic()));
        Long user=LoginHelper.getUserId();
        try {ownership.requireOwned(user,request.sessionId());}catch(IllegalArgumentException e){throw new ServiceException("检索会话不可用",403);}
        var session=sessions.queryById(request.sessionId());
        if(session==null||session.getSessionTitle()==null||!session.getSessionTitle().startsWith("案情检索 - "))throw new ServiceException("请从案情检索入口创建会话",400);
        var ids=knowledge.scope(request.knowledgeIds());
        // Uses the existing first-effective-message rule. Extraction and retrieval do not call chat/send.
        usage.beginProcessing(LoginHelper.getTenantId(),user,request.sessionId());
        var warnings=new ArrayList<String>();boolean fallback=false;FactElements elements;
        try {elements=model.extract(request.text());}
        catch(Exception e) {if(e instanceof InterruptedException)Thread.currentThread().interrupt();elements=FactElements.fallback(request.text());fallback=true;warnings.add("要素自动抽取暂不可用，已使用案情关键词全文检索");}
        var found=knowledge.search(ids,elements,fallback,filter);warnings.addAll(found.warnings());
        if(request.knowledgeIds()==null||request.knowledgeIds().isEmpty()) {
            int readable=access.readableIds().size();
            if(readable>ids.size())warnings.add("当前可访问"+readable+"个知识库，本次仅检索其中前"+ids.size()+"个（按知识库ID排序），未覆盖其余知识库；可手动选择最多4个库重新检索");
        }
        var hits=found.hits().stream().map(hit->{String content=Objects.toString(hit.getContent(),"");return new Hit(hit.getId(),hit.getKnowledgeId(),hit.getSourceName(),content.substring(0,Math.min(6000,content.length())),content.length()>6000,hit.getReference(),hit.getLegalMetadata());}).toList();
        String message=hits.isEmpty()?(ids.isEmpty()?"暂无可访问知识库，请先添加资料或获取知识库访问权限":found.warnings().contains("关键词检索暂不可用，请稍后重试")?"检索服务暂不可用，请稍后重试":"所选知识库暂无匹配，可调整案由关键词、筛选条件或选择其他知识库；不代表不存在相关法规或案例"):
            "找到 "+hits.size()+" 个知识库片段";
        var result=new Result(request.sessionId(),elements,fallback?"fallback":"model",fallback||found.degraded()?"keyword":"hybrid",FactSearchRelevance.query(elements),found.attemptedLibraries(),hits,List.copyOf(warnings),message);
        messages.saveChatMessage(user,request.sessionId(),request.text(),"user","案情检索");
        messages.saveChatMessage(user,request.sessionId(),snapshot(result),"assistant","案情检索");
        return result;
    }
    private String snapshot(Result result) {
        try {
            var out=new StringBuilder("## 案情检索要素\n\n```json\n"+new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(result.elements())+"\n```\n\n"+result.message()+"\n");
            for(var warning:result.warnings())out.append("\n提示：").append(warning).append('\n');
            for(var hit:result.hits()) {out.append("\n### ").append(hit.sourceName()==null?"知识库片段":hit.sourceName()).append("\n\n").append(hit.content()).append('\n');if(hit.reference()!=null)out.append("\n```legal-citations\n[").append(hit.reference().toJson()).append("]\n```\n");}
            return out.toString();
        }catch(Exception e){throw new ServiceException("检索结果保存失败，请重试",500);}
    }
}
