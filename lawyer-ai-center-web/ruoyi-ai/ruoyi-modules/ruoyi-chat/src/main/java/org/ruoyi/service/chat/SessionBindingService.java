package org.ruoyi.service.chat;
import com.fasterxml.jackson.databind.*;
import org.ruoyi.common.chat.domain.dto.request.ChatRequest;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class SessionBindingService {
 private final JdbcTemplate jdbc;
 private final ChatSessionOwnershipGuard ownership;
 private static final ObjectMapper JSON=new ObjectMapper();
 private Long owner(Long sessionId){
  Long id=LoginHelper.getUserId();if(id==null||LoginHelper.getTenantId()==null)throw new ServiceException("请先登录",401);
  try{ownership.requireOwned(id,sessionId);}catch(IllegalArgumentException e){throw new ServiceException("会话不可用",404);}return id;
 }
 public static JsonNode validate(JsonNode value){
  if(value==null||value.toString().length()>100000)throw new ServiceException("会话设置过长",400);
  String mode=value.path("mode").asText();
  if(!java.util.Set.of("model","agent","workflow").contains(mode))throw new ServiceException("会话模式无效",400);
  if("agent".equals(mode)&&!value.path("agentId").asText().matches("[1-9][0-9]{0,18}"))throw new ServiceException("助手标识无效",400);
  if("workflow".equals(mode)&&!value.path("workflow").path("uuid").asText().matches("[A-Za-z0-9_-]{1,100}"))throw new ServiceException("流程标识无效",400);
  return value;
 }
 public JsonNode load(Long sessionId){
  Long user=owner(sessionId);
  var rows=jdbc.queryForList("SELECT binding_json FROM lawyer_session_binding WHERE tenant_id=? AND session_id=? AND user_id=?",String.class,LoginHelper.getTenantId(),sessionId,user);
  if(rows.isEmpty())return JSON.createObjectNode().put("mode","model");
  try{return validate(JSON.readTree(rows.get(0)));}catch(java.io.IOException e){throw new ServiceException("会话设置损坏，请重新选择助手",409);}
 }
 public void save(Long sessionId,JsonNode value){
  Long user=owner(sessionId);validate(value);
  try {
   jdbc.update("INSERT INTO lawyer_session_binding(tenant_id,session_id,user_id,binding_json) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE binding_json=VALUES(binding_json),updated_at=CURRENT_TIMESTAMP",
    LoginHelper.getTenantId(),sessionId,user,value.toString());
  } catch(org.springframework.jdbc.BadSqlGrammarException error) {
   // Log the database exception for operators without returning SQL or credentials.
   log.error("Session binding schema unavailable; apply 2026-10-19-session-binding.sql",error);
   throw new org.ruoyi.service.legal.DraftingException("会话配置尚未升级，请管理员执行会话绑定数据库迁移",503);
  }
 }
 /** Every normal HTTP/SSE producer, including drafting and contract review, records its first use. */
 public void capture(ChatRequest request){
  if(request.getSessionId()==null)return;
  var value=JSON.createObjectNode();value.put("model",request.getModel());
  if(Boolean.TRUE.equals(request.getEnableWorkFlow())&&request.getWorkFlowRunner()!=null){
   value.put("mode","workflow");value.set("workflow",JSON.valueToTree(request.getWorkFlowRunner()));
  }else if(request.getAgentId()!=null){value.put("mode","agent");value.put("agentId",request.getAgentId().toString());}
  else value.put("mode","model");
  save(request.getSessionId(),value);
 }
}
