package org.ruoyi.controller.legal;
import org.junit.jupiter.api.*;
import org.ruoyi.service.agent.IAgentService;
import org.ruoyi.common.chat.service.chat.IChatModelService;
import org.ruoyi.domain.vo.agent.AgentVo;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.service.legal.DraftingException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@Tag("dev")
class DraftingControllerTest {
 @Test void missingOrDisabledAgentHasSpecificMessage(){
  var agents=mock(IAgentService.class);var models=mock(IChatModelService.class);
  when(agents.queryEnabledOptions()).thenReturn(java.util.List.of());
  var error=assertThrows(DraftingException.class,()->new DraftingController(agents,models).prepare());
  assertEquals("未配置或未启用文书起草助手",error.getMessage());verifyNoInteractions(models);
 }
 @Test void missingModelAndKeyAreSpecificAndReadyNeverExposesSecrets(){
  var agents=mock(IAgentService.class);var models=mock(IChatModelService.class);
  var a=new AgentVo();a.setId(4L);a.setAgentName("文书起草助手");a.setStatus("0");a.setModelId(7L);
  when(agents.queryEnabledOptions()).thenReturn(java.util.List.of(a));
  var controller=new DraftingController(agents,models);
  assertTrue(assertThrows(DraftingException.class,controller::prepare).getMessage().contains("模型"));
  var m=new ChatModelVo();m.setCategory("chat");m.setModelName("example");m.setProviderCode("OpenAI");m.setApiHost("https://example.test");
  when(models.queryById(7L)).thenReturn(m);
  assertEquals("模型密钥未配置",assertThrows(DraftingException.class,controller::prepare).getMessage());
  m.setApiKey("test-secret");when(models.selectModelByName("example")).thenReturn(m);var r=controller.prepare().getData();assertEquals("4",r.agentId());assertEquals("example",r.model());assertFalse(r.toString().contains("secret"));
  m.setApiKey(null);m.setProviderCode("vllm");assertEquals("example",controller.prepare().getData().model());
  when(models.selectModelByName("example")).thenThrow(new org.ruoyi.common.core.exception.ServiceException("internal"));
  assertEquals("模型厂商未配置或已停用",assertThrows(DraftingException.class,controller::prepare).getMessage());
 }
}
