package org.ruoyi.websocket.chat;

import cn.dev33.satoken.util.SaFoxUtil;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.chat.domain.bo.chat.ChatModelBo;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.ruoyi.common.core.domain.model.LoginUser;
import org.ruoyi.common.tenant.helper.TenantHelper;
import org.ruoyi.domain.vo.agent.AgentVo;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Explicit model choices apply only to legal consultation; other agent policies stay fixed. */
@Component
@RequiredArgsConstructor
public class ConsultationModelResolver {
    private final IChatModelService models;

    public ChatModelVo requireSelected(LoginUser principal, AgentVo agent, String selectedId) {
        if (principal == null || principal.getUserId() == null || principal.getTenantId() == null
            || principal.getTenantId().isBlank()) {
            throw new IllegalArgumentException("请重新登录后选择模型");
        }
        if (agent == null || !"0".equals(agent.getStatus()) || !"法律咨询助手".equals(agent.getAgentName())) {
            throw new IllegalArgumentException("该助手使用固定模型，请从法律咨询切换模型");
        }
        var permissions = principal.getMenuPermission();
        boolean permitted = permissions != null && permissions.stream().anyMatch(permission -> permission != null
            && (SaFoxUtil.vagueMatch(permission, "system:model:list")
            || SaFoxUtil.vagueMatch(permission, "coding:harness:use")));
        if (!permitted) throw new IllegalArgumentException("当前账号无权选择模型");
        Long id;
        try {
            if (selectedId == null || !selectedId.matches("[1-9][0-9]{0,18}")) throw new NumberFormatException();
            id = Long.valueOf(selectedId);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("模型标识无效，请重新选择");
        }
        ChatModelBo query = new ChatModelBo();
        query.setCategory("chat");
        return TenantHelper.threadScope(principal.getTenantId(), () -> models.queryAvailableList(query).stream()
            .filter(model -> Objects.equals(id, model.getId()) && "chat".equals(model.getCategory()))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("所选模型已不可用，请重新选择")));
    }
}
