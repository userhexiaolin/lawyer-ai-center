package org.ruoyi.service.legal;

import lombok.RequiredArgsConstructor;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.Optional;

/** Immutable suggestions: an INSERT race returns the first stored result to every caller. */
@Service @RequiredArgsConstructor
public class TemplateTypeDecisionStore {
 private final JdbcTemplate jdbc;
 public record Decision(String documentType,String method,String evidence){}
 private String tenant(){
  String tenant=LoginHelper.getTenantId();
  if(LoginHelper.getUserId()==null||tenant==null||tenant.isBlank())throw new DraftingException("请先登录",401);
  return tenant;
 }
 public Optional<Decision> find(String fingerprint){
  return jdbc.query("SELECT document_type,method,evidence FROM legal_doc_template_type_decision WHERE tenant_id=? AND fingerprint=?",
   (rs,index)->new Decision(rs.getString("document_type"),rs.getString("method"),rs.getString("evidence")),tenant(),fingerprint).stream().findFirst();
 }
 public Decision retain(String fingerprint,String sourceDigest,Long modelId,String modelName,String policyVersion,Decision decision){
  jdbc.update("INSERT INTO legal_doc_template_type_decision(tenant_id,fingerprint,source_digest,document_type,method,evidence,model_id,model_name,policy_version,created_by) VALUES (?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE fingerprint=VALUES(fingerprint)",
   tenant(),fingerprint,sourceDigest,decision.documentType(),decision.method(),decision.evidence(),modelId,modelName,policyVersion,LoginHelper.getUserId());
  return find(fingerprint).orElseThrow(()->new IllegalStateException("Missing classification decision"));
 }
}
