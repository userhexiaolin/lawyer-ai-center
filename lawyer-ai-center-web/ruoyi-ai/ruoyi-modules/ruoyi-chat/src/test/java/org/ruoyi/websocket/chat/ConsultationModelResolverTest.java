package org.ruoyi.websocket.chat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ruoyi.common.chat.domain.bo.chat.ChatModelBo;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.ruoyi.common.core.domain.model.LoginUser;
import org.ruoyi.domain.vo.agent.AgentVo;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class ConsultationModelResolverTest {
    private final IChatModelService models = mock(IChatModelService.class);
    private final ConsultationModelResolver resolver = new ConsultationModelResolver(models);
    static LoginUser principal(String permission) {
        LoginUser user = new LoginUser(); user.setUserId(7L); user.setTenantId("synthetic");
        user.setMenuPermission(Set.of(permission)); return user;
    }
    static AgentVo agent(String name) { AgentVo agent = new AgentVo(); agent.setId(2L); agent.setStatus("0"); agent.setAgentName(name); agent.setModelId(11L); return agent; }
    static ChatModelVo model(long id, String category) { ChatModelVo model = new ChatModelVo(); model.setId(id); model.setCategory(category); model.setModelName("synthetic-"+id); model.setProviderCode("synthetic"); return model; }
    @Test void choosesExactLongIdFromAvailableChatModels() {
        ChatModelVo chosen = model(90071992547409937L,"chat"); when(models.queryAvailableList(any())).thenReturn(List.of(model(12L,"chat"),chosen));
        assertSame(chosen,resolver.requireSelected(principal("system:model:list"),agent("法律咨询助手"),"90071992547409937"));
        verify(models).queryAvailableList(argThat(query -> "chat".equals(query.getCategory()))); verify(models,never()).selectModelByName(any());
    }
    @Test void honorsSameWildcardAndHarnessPermissionsAsModelList() {
        when(models.queryAvailableList(any())).thenReturn(List.of(model(12L,"chat")));
        for(String permission:List.of("*:*:*","system:*","coding:harness:use")) assertNotNull(resolver.requireSelected(principal(permission),agent("法律咨询助手"),"12"));
    }
    @Test void cannotOverrideReviewDraftingOrUnidentifiedAgents() {
        for(String name:List.of("合同审查官","文书起草助手")) assertThrows(IllegalArgumentException.class,()->resolver.requireSelected(principal("*:*:*"),agent(name),"12"));
        assertThrows(IllegalArgumentException.class,()->resolver.requireSelected(principal("*:*:*"),null,"12")); verifyNoInteractions(models);
    }
    @Test void missingPermissionOrPrincipalCannotQueryModels() {
        assertThrows(IllegalArgumentException.class,()->resolver.requireSelected(principal("legal:consult"),agent("法律咨询助手"),"12"));
        assertThrows(IllegalArgumentException.class,()->resolver.requireSelected(null,agent("法律咨询助手"),"12")); verifyNoInteractions(models);
    }
    @Test void removedDisabledOrNonChatChoiceNeverFallsBack() {
        when(models.queryAvailableList(any())).thenReturn(List.of(model(13L,"chat"),model(12L,"embedding")));
        var error=assertThrows(IllegalArgumentException.class,()->resolver.requireSelected(principal("*:*:*"),agent("法律咨询助手"),"12"));
        assertTrue(error.getMessage().contains("不可用")); verify(models,never()).selectModelByName(any());
    }
    @Test void malformedOrOverflowIdsDoNotReachRegistry() {
        for(String id:List.of("0","-1","1.5","9e18","9223372036854775808","invalid")) assertThrows(IllegalArgumentException.class,()->resolver.requireSelected(principal("*:*:*"),agent("法律咨询助手"),id)); verifyNoInteractions(models);
    }
}
