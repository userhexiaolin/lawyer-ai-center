package org.ruoyi.service.chat;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.ruoyi.common.core.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
@Tag("dev")
class SessionBindingServiceTest {
 @Test void contextValidationKeepsLongIdAsTextAndRejectsInvalidModes()throws Exception{
  var json=new ObjectMapper();
  var node=SessionBindingService.validate(json.readTree("{\"mode\":\"agent\",\"agentId\":\"2111646391498584065\"}"));
  assertEquals("2111646391498584065",node.path("agentId").asText());
  assertThrows(ServiceException.class,()->SessionBindingService.validate(json.readTree("{\"mode\":\"other\"}")));
  assertThrows(ServiceException.class,()->SessionBindingService.validate(json.readTree("{\"mode\":\"workflow\"}")));
 }
 @Test void foreignSessionCannotReadOrOverwriteBindings()throws Exception{
  var jdbc=mock(JdbcTemplate.class);var guard=mock(ChatSessionOwnershipGuard.class);var service=new SessionBindingService(jdbc,guard);
  try(var login=mockStatic(LoginHelper.class)){
   login.when(LoginHelper::getUserId).thenReturn(1L);login.when(LoginHelper::getTenantId).thenReturn("tenant-a");
   doThrow(new IllegalArgumentException()).when(guard).requireOwned(1L,2L);
   assertThrows(ServiceException.class,()->service.load(2L));
   assertThrows(ServiceException.class,()->service.save(2L,new ObjectMapper().readTree("{\"mode\":\"model\"}")));
   verifyNoInteractions(jdbc);
  }
 }
}
