package org.ruoyi.controller.legal;
import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.domain.R;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.ruoyi.service.agent.IAgentService;
import org.ruoyi.service.legal.DraftingException;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @SaCheckLogin
@RequestMapping("/legal/drafting")
public class DraftingController {
    private final IAgentService agents;
    private final IChatModelService models;
    public record Ready(String agentId,String model){}
    @GetMapping("/prepare") public R<Ready> prepare(){
        var agent=agents.queryEnabledOptions().stream().filter(a->"文书起草助手".equals(a.getAgentName())&&"0".equals(a.getStatus()))
            .min(java.util.Comparator.comparing(org.ruoyi.domain.vo.agent.AgentVo::getId))
            .orElseThrow(()->new DraftingException("未配置或未启用文书起草助手",422));
        if(agent.getModelId()==null)throw new DraftingException("文书起草助手未绑定可用模型",422);
        var model=models.queryById(agent.getModelId());
        if(model==null||blank(model.getModelName())||!"chat".equals(model.getCategory()))throw new DraftingException("未配置可用的聊天模型",422);
        if(blank(model.getProviderCode())||blank(model.getApiHost()))throw new DraftingException("模型厂商或服务地址未配置",422);
        boolean keyless=java.util.Set.of("ollama","vllm").contains(model.getProviderCode().toLowerCase(java.util.Locale.ROOT));
        if(blank(model.getApiKey())&&!keyless)throw new DraftingException("模型密钥未配置",422);
        try {
            // Normal routing also requires the provider to be enabled.
            if(models.selectModelByName(model.getModelName())==null)throw new DraftingException("未配置可用模型",422);
        } catch(org.ruoyi.common.core.exception.ServiceException error) {
            throw new DraftingException("模型厂商未配置或已停用",422);
        }
        return R.ok(new Ready(agent.getId().toString(),model.getModelName()));
    }
    private static boolean blank(String value){return value==null||value.isBlank();}
}
