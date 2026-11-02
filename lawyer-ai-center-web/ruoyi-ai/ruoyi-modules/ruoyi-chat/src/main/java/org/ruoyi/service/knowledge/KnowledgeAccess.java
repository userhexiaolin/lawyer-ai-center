package org.ruoyi.service.knowledge;

import lombok.RequiredArgsConstructor;
import org.ruoyi.common.core.exception.ServiceException;
import org.ruoyi.common.satoken.utils.LoginHelper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.List;

/** Ownership checks precede every synchronous knowledge operation and cached retrieval. */
@Component @RequiredArgsConstructor
public class KnowledgeAccess {
    private final JdbcTemplate jdbc;
    private boolean admin(){return LoginHelper.isSuperAdmin()||LoginHelper.isTenantAdmin();}
    private String tenant(){String value=LoginHelper.getTenantId();if(value==null||LoginHelper.getUserId()==null)deny();return value;}
    private void deny(){throw new ServiceException("知识库或文件不存在，或无访问权限",403);}
    public List<Long> readableIds(){String t=tenant();return jdbc.queryForList("SELECT id FROM knowledge_info WHERE tenant_id=? AND (? OR user_id=? OR share=1)",Long.class,t,admin(),LoginHelper.getUserId());}
    public void read(Long id){check(id,false);}
    public void readFor(Long id,Long user,String tenant){
        if(id==null||user==null||tenant==null)deny();
        Long n=jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_info WHERE id=? AND tenant_id=? AND (user_id=? OR share=1)",Long.class,id,tenant,user);
        if(n==null||n!=1)deny();
    }
    public void write(Long id){check(id,true);}
    private void check(Long id,boolean writing){String t=tenant();if(id==null)deny();Long n=jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_info WHERE id=? AND tenant_id=? AND (? OR user_id=? "+(writing?"":"OR share=1")+")",Long.class,id,t,admin(),LoginHelper.getUserId());if(n==null||n!=1)deny();}
    private Long parent(String table,Long id){String t=tenant();List<Long> ids=jdbc.queryForList("SELECT knowledge_id FROM "+table+" WHERE id=? AND tenant_id=?",Long.class,id,t);if(ids.size()!=1)deny();return ids.get(0);}
    public void readAttach(Long id){read(parent("knowledge_attach",id));}
    public void writeAttach(Long id){write(parent("knowledge_attach",id));}
    public void readFragment(Long id){read(parent("knowledge_fragment",id));}
    public void writeFragment(Long id){write(parent("knowledge_fragment",id));}
    public void ownedOss(Long id){if(id==null)return;String t=tenant();Long n=jdbc.queryForObject("SELECT COUNT(*) FROM sys_oss WHERE oss_id=? AND tenant_id=? AND (? OR create_by=?)",Long.class,id,t,admin(),LoginHelper.getUserId());if(n==null||n!=1)deny();}
    public void document(Long kid,String docId){Long n=jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_attach WHERE knowledge_id=? AND doc_id=? AND tenant_id=?",Long.class,kid,docId,tenant());if(n==null||n!=1)deny();}
}
