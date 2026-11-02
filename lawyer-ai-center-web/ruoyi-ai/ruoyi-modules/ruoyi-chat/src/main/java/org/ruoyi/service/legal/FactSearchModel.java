package org.ruoyi.service.legal;

import dev.langchain4j.data.message.*;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.ruoyi.factory.ChatServiceFactory;
import org.ruoyi.service.agent.IAgentService;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.*;

/** Bounded extraction deadline. Model/agent configuration is resolved on the authenticated request thread. */
@Component @RequiredArgsConstructor
public class FactSearchModel {
    private final IAgentService agents;
    private final IChatModelService models;
    private final ChatServiceFactory factory;
    private final ExecutorService workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->{var t=new Thread(r,"fact-extraction");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    @jakarta.annotation.PreDestroy public void close(){workers.shutdownNow();}
    public FactElements extract(String text) throws Exception {
        var agent=agents.queryEnabledOptions().stream().filter(a->"法律咨询助手".equals(a.getAgentName())&&a.getModelId()!=null).findFirst().orElseThrow(()->new IllegalStateException("Extraction model unavailable"));
        var config=models.queryById(agent.getModelId());if(config==null)throw new IllegalStateException("Extraction model unavailable");
        var provider=factory.getOriginalService(config.getProviderCode());
        var future=workers.submit(()->provider.buildChatModel(config).chat(List.of(SystemMessage.from(FactElements.PROMPT),UserMessage.from(text))).aiMessage().text());
        try {return FactElements.parse(future.get(15,TimeUnit.SECONDS)).groundedIn(text);}
        finally {future.cancel(true);}
    }
}
