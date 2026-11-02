package org.ruoyi.service.lawyer;

import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.factory.ChatServiceFactory;
import org.ruoyi.service.coding.harness.loop.model.*;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Uses the project's actual provider implementation; tests may replace this seam, production never synthesizes claims. */
@Service @RequiredArgsConstructor
public class DossierClaimModel {
    private final IChatModelService models;
    private final ChatServiceFactory providers;
    private final DossierAiProperties properties;
    private final ScheduledExecutorService timeouts=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"dossier-model-timeouts");t.setDaemon(true);return t;});
    public record Route(Long id,String modelName,String provider,String endpoint,String region){}
    public record Output(String json,Long inputTokens,Long outputTokens){}
    public Route route(String tenant,Long id){var model=model(tenant,id);String region=properties.getModelRegions().get(id);if(region==null||region.isBlank())throw new ServiceException("模型处理地域未配置，请联系管理员核准后使用；手工摘录仍可用",503);return new Route(id,model.getModelName(),model.getProviderCode(),model.getApiHost(),region);}
    private ChatModelVo model(String tenant,Long id){var model=TenantHelper.threadScope(tenant,()->models.queryAvailableList(new org.ruoyi.common.chat.domain.bo.chat.ChatModelBo()).stream().filter(m->Objects.equals(m.getId(),id)).findFirst().orElse(null));if(model==null||model.getApiHost()==null||model.getApiKey()==null||model.getApiKey().isBlank())throw new ServiceException("模型配置不可用，手工摘录仍可用",503);return model;}
    public Output generate(String tenant,Long actor,Route expected,String data){var model=model(tenant,expected.id());if(!expected.equals(new Route(model.getId(),model.getModelName(),model.getProviderCode(),model.getApiHost(),properties.getModelRegions().get(expected.id()))))throw new ServiceException("模型路由变化，请重新确认授权范围",409);var request=new org.ruoyi.common.chat.domain.dto.request.ChatRequest();request.setModel(model.getModelName());request.setContent("案件事实候选结构化抽取");request.setUserId(actor);request.setEnableThinking(false);request.setReplayThinking(false);request.setChatModelVo(model);
        var streaming=providers.getOriginalService(model.getProviderCode()).buildStreamingChatModel(model,request);
        var input=dev.langchain4j.model.chat.request.ChatRequest.builder().messages(List.of(SystemMessage.from(DossierClaimSchema.prompt()),UserMessage.from("UNTRUSTED_SOURCE_DATA_JSON\n"+data))).parameters(ChatRequestParameters.builder().maxOutputTokens(2500).build()).build();
        try{var response=new StreamingModelTurnAdapter(streaming,timeouts,Clock.systemUTC()).execute(input,Duration.ofSeconds(90),ModelTurnListener.NOOP).response();var usage=response.tokenUsage();Long in=usage==null||usage.inputTokenCount()==null?null:usage.inputTokenCount().longValue(),out=usage==null||usage.outputTokenCount()==null?null:usage.outputTokenCount().longValue();return new Output(response.aiMessage().text(),in,out);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new ServiceException("模型任务已中断，已完成块保留",503);}catch(Exception e){throw new ServiceException("模型服务暂不可用，原件、人工修改和已完成块保留",503);}
    }
    @jakarta.annotation.PreDestroy public void close(){timeouts.shutdownNow();}
}
