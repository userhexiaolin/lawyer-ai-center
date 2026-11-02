package org.ruoyi.service.lawyer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Field-level view over existing immutable business audit; no UPDATE/DELETE or second audit store. */
@Service @RequiredArgsConstructor
public class DossierReviewAudit {
    private final JdbcTemplate jdbc;
    private static final ObjectMapper JSON=new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    public record FieldChange(String field,String oldValue,String newValue,boolean sensitive,String protectedRef){}
    public record Entry(String id,String action,String operatorId,String createTime,String reason,String beforeFingerprint,String afterFingerprint,List<FieldChange> fields){}
    List<Map<String,Object>> rows(DossierAnchorService.Principal p,String kind,Long objectId){
        if("event".equals(kind))return jdbc.queryForList("SELECT CAST(id AS CHAR) id,action,CAST(operator_id AS CHAR) operatorId,DATE_FORMAT(create_time,'%Y-%m-%dT%H:%i:%sZ') createTime,reason,before_json beforeJson,after_json afterJson FROM lawyer_case_review_change WHERE tenant_id=? AND case_id=? AND object_kind='event' AND object_id=? ORDER BY create_time,id",p.tenantId(),p.caseId(),objectId);
        if("claim".equals(kind))return jdbc.queryForList("SELECT CAST(id AS CHAR) id,action,CAST(operator_id AS CHAR) operatorId,DATE_FORMAT(create_time,'%Y-%m-%dT%H:%i:%sZ') createTime,reason,before_json beforeJson,after_json afterJson FROM lawyer_case_review_change WHERE tenant_id=? AND case_id=? AND object_kind='claim' AND object_id=? ORDER BY create_time,id",p.tenantId(),p.caseId(),objectId);
        if("bank".equals(kind))return jdbc.queryForList("SELECT CAST(id AS CHAR) id,action,CAST(operator_id AS CHAR) operatorId,DATE_FORMAT(create_time,'%Y-%m-%dT%H:%i:%sZ') createTime,reason,before_json beforeJson,after_json afterJson FROM lawyer_case_bank_change WHERE tenant_id=? AND case_id=? AND row_id=? ORDER BY create_time,id",p.tenantId(),p.caseId(),objectId);
        throw new ServiceException("该对象尚未接入统一复核",503);
    }
    public List<Entry> history(DossierAnchorService.Principal p,String kind,Long id){var out=new ArrayList<Entry>();for(var row:rows(p,kind,id)){String audit=row.get("id").toString(),before=Objects.toString(row.get("beforeJson"),"{}"),after=Objects.toString(row.get("afterJson"),"{}");out.add(new Entry(audit,Objects.toString(row.get("action"),""),Objects.toString(row.get("operatorId"),""),Objects.toString(row.get("createTime"),""),BankStatementParser.safeText(Objects.toString(row.get("reason"),"")),fingerprint(before),fingerprint(after),diff(before,after,kind+"/"+id+"/audit/"+audit)));}return out;}
    Map<String,Object> original(DossierAnchorService.Principal p,String kind,Long id){var rows=rows(p,kind,id);if(rows.isEmpty())return Map.of();var first=rows.get(0);var before=parse(Objects.toString(first.get("beforeJson"),"{}"));return object(before.isEmpty()?parse(Objects.toString(first.get("afterJson"),"{}")):before);}
    static Map<String,Object> object(Map<String,Object> value){Object entity=value.containsKey("claim")?value.get("claim"):value.get("event");if(entity instanceof Map<?,?> nested){var out=new LinkedHashMap<String,Object>();nested.forEach((k,v)->out.put(k.toString(),v));return out;}return value;}
    static Map<String,Object> parse(String raw){try{if(raw.length()>1_000_000)throw new IllegalArgumentException();return JSON.readValue(raw,new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception e){throw new ServiceException("审校历史结构损坏，请保留记录并联系管理员",422);}}
    static String fingerprint(String raw){try{return DossierAnchorService.hash(JSON.writeValueAsString(parse(raw)));}catch(ServiceException e){throw e;}catch(Exception e){throw new ServiceException("审校指纹不可计算",422);}}
    static boolean sensitive(String field){return field.toLowerCase(Locale.ROOT).matches(".*(?:account|counterparty|participants|(?:^|\\.)name$|identity|idcard|phone|mobile|subjectname).* ".stripTrailing());}
    static String display(String field,Object value){String text=value==null?"未知":value instanceof String s?s:BankStatementService.json(value);return sensitive(field)?Objects.toString(BankStatementParser.mask(text),"未知"):BankStatementParser.safeText(text);}
    static void flatten(String prefix,Object value,Map<String,Object> out){
        if(value instanceof Map<?,?> map){map.forEach((key,item)->flatten(prefix.isBlank()?key.toString():prefix+"."+key,item,out));return;}
        if(prefix.endsWith("valuesJson")&&value instanceof String raw){try{flatten(prefix.substring(0,prefix.length()-4),parse(raw),out);return;}catch(ServiceException ignored){}}
        out.put(prefix,value);
    }
    public static List<FieldChange> diff(String before,String after,String reference){var left=new TreeMap<String,Object>();var right=new TreeMap<String,Object>();flatten("",parse(before),left);flatten("",parse(after),right);var keys=new TreeSet<String>();keys.addAll(left.keySet());keys.addAll(right.keySet());var changes=new ArrayList<FieldChange>();for(String key:keys)if(!Objects.equals(left.get(key),right.get(key)))changes.add(new FieldChange(key,display(key,left.get(key)),display(key,right.get(key)),sensitive(key),reference+"#"+key));return changes;}
}
