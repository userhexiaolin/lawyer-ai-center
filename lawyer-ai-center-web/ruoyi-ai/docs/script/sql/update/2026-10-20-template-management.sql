-- Apply after 2026-10-19-legal-doc-templates.sql. Re-runnable; deploy both clients and backend.
CREATE TABLE IF NOT EXISTS legal_doc_template_type (
 id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY, tenant_id varchar(20) NOT NULL,
 name varchar(50) NOT NULL, sort_order int NOT NULL DEFAULT 0,
 status char(1) NOT NULL DEFAULT '0', builtin tinyint(1) NOT NULL DEFAULT 0, remark varchar(500),
 create_dept bigint, create_by bigint, create_time datetime, update_by bigint, update_time datetime,
 UNIQUE KEY uk_template_type_name(tenant_id,name), KEY idx_template_type_order(tenant_id,status,sort_order,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed existing tenants and preserve all later edits. New tenants are initialized on first access.
INSERT INTO legal_doc_template_type(tenant_id,name,sort_order,status,builtin,remark)
SELECT t.tenant_id,d.name,d.sort_order,'0',1,'系统内置类型'
FROM (SELECT tenant_id FROM sys_tenant UNION SELECT tenant_id FROM legal_doc_template UNION SELECT '000000') t
CROSS JOIN (
 SELECT '民事起诉状' name,10 sort_order UNION ALL SELECT '民事答辩状',20
 UNION ALL SELECT '律师函',30 UNION ALL SELECT '合同文书',40
 UNION ALL SELECT '法律意见书',50 UNION ALL SELECT '自由描述文书',60
) d
WHERE NOT EXISTS(SELECT 1 FROM legal_doc_template_type e WHERE e.tenant_id=t.tenant_id AND e.name=d.name);

CREATE TABLE IF NOT EXISTS template_menu_20261020_backup LIKE sys_menu;
INSERT IGNORE INTO template_menu_20261020_backup SELECT * FROM sys_menu WHERE perms='legal:doc-template:list';
CREATE TABLE IF NOT EXISTS template_menu_20261020_created(menu_id bigint PRIMARY KEY);
INSERT IGNORE INTO template_menu_20261020_created SELECT 2099102000000000100 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE parent_id=0 AND path='templates');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,icon)
SELECT 2099102000000000100,'模板管理',0,25,'templates','',1,0,'M','0','0','lucide:files'
WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE parent_id=0 AND path='templates');
SET @template_root=(SELECT menu_id FROM sys_menu WHERE parent_id=0 AND path='templates' LIMIT 1);
SET @template_list=(SELECT menu_id FROM sys_menu WHERE perms='legal:doc-template:list' LIMIT 1);
-- Preserve the existing URL and existing template CRUD/button IDs.
UPDATE sys_menu SET parent_id=@template_root,path='/knowledge/doc-template',order_num=20,visible='0'
WHERE menu_id=@template_list AND @template_root IS NOT NULL;

INSERT IGNORE INTO template_menu_20261020_created SELECT 2099102000000000110 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='legal:doc-template-type:list');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon)
SELECT 2099102000000000110,'模板类型',@template_root,10,'type','legal/doc-template-type/index',1,0,'C','0','0','legal:doc-template-type:list','lucide:tags'
WHERE @template_root IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='legal:doc-template-type:list');
SET @template_types=(SELECT menu_id FROM sys_menu WHERE perms='legal:doc-template-type:list' LIMIT 1);

INSERT IGNORE INTO template_menu_20261020_created SELECT 2099102000000000111 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='legal:doc-template-type:add');
INSERT IGNORE INTO template_menu_20261020_created SELECT 2099102000000000112 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='legal:doc-template-type:edit');
INSERT IGNORE INTO template_menu_20261020_created SELECT 2099102000000000113 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='legal:doc-template-type:remove');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,menu_type,visible,status,perms)
SELECT 2099102000000000111,'类型新增',@template_types,1,'F','0','0','legal:doc-template-type:add'
WHERE @template_types IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='legal:doc-template-type:add');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,menu_type,visible,status,perms)
SELECT 2099102000000000112,'类型修改及启停',@template_types,2,'F','0','0','legal:doc-template-type:edit'
WHERE @template_types IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='legal:doc-template-type:edit');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,menu_type,visible,status,perms)
SELECT 2099102000000000113,'类型删除',@template_types,3,'F','0','0','legal:doc-template-type:remove'
WHERE @template_types IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='legal:doc-template-type:remove');

-- Match existing template privileges: list -> list, add -> add, edit -> edit, remove -> remove.
CREATE TABLE IF NOT EXISTS template_menu_20261020_role_added(role_id bigint,menu_id bigint,PRIMARY KEY(role_id,menu_id));
INSERT IGNORE INTO template_menu_20261020_role_added
SELECT DISTINCT rm.role_id,n.menu_id FROM sys_role_menu rm JOIN sys_menu old ON old.menu_id=rm.menu_id
JOIN sys_menu n ON n.perms=REPLACE(old.perms,'legal:doc-template:','legal:doc-template-type:')
LEFT JOIN sys_role_menu existing ON existing.role_id=rm.role_id AND existing.menu_id=n.menu_id
WHERE old.perms IN ('legal:doc-template:list','legal:doc-template:add','legal:doc-template:edit','legal:doc-template:remove') AND existing.menu_id IS NULL;
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT a.role_id,a.menu_id FROM template_menu_20261020_role_added a
LEFT JOIN sys_role_menu e ON e.role_id=a.role_id AND e.menu_id=a.menu_id WHERE e.menu_id IS NULL;
INSERT IGNORE INTO template_menu_20261020_role_added
SELECT DISTINCT rm.role_id,@template_root FROM sys_role_menu rm
LEFT JOIN sys_role_menu e ON e.role_id=rm.role_id AND e.menu_id=@template_root
WHERE rm.menu_id IN (@template_list,@template_types) AND @template_root IS NOT NULL AND e.menu_id IS NULL;
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT a.role_id,a.menu_id FROM template_menu_20261020_role_added a
LEFT JOIN sys_role_menu e ON e.role_id=a.role_id AND e.menu_id=a.menu_id WHERE e.menu_id IS NULL;

-- Tenant packages inherit matching privileges and the new ancestor only.
CREATE TABLE IF NOT EXISTS template_menu_20261020_package_backup LIKE sys_tenant_package;
INSERT IGNORE INTO template_menu_20261020_package_backup SELECT * FROM sys_tenant_package;
UPDATE sys_tenant_package p SET menu_ids=CONCAT_WS(',',NULLIF(p.menu_ids,''),CAST(@template_root AS CHAR))
WHERE @template_root IS NOT NULL AND NOT FIND_IN_SET(@template_root,COALESCE(p.menu_ids,'')) AND FIND_IN_SET(@template_list,COALESCE(p.menu_ids,''));
UPDATE sys_tenant_package p SET menu_ids=CONCAT_WS(',',NULLIF(p.menu_ids,''),
 (SELECT GROUP_CONCAT(n.menu_id ORDER BY n.menu_id) FROM sys_menu old JOIN sys_menu n
 ON n.perms=REPLACE(old.perms,'legal:doc-template:','legal:doc-template-type:')
 WHERE old.perms IN ('legal:doc-template:list','legal:doc-template:add','legal:doc-template:edit','legal:doc-template:remove')
 AND FIND_IN_SET(old.menu_id,COALESCE(p.menu_ids,'')) AND NOT FIND_IN_SET(n.menu_id,COALESCE(p.menu_ids,''))))
WHERE FIND_IN_SET(@template_list,COALESCE(p.menu_ids,''));
-- Re-sync tenant packages and re-login to refresh permissions/menu caches after rollout.
