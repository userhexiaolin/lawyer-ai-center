-- Instruction 83. Apply after 80/81 and session-binding migration. Re-runnable.
CREATE TABLE IF NOT EXISTS legal_doc_template (
 id bigint NOT NULL PRIMARY KEY,tenant_id varchar(20) NOT NULL,
 name varchar(100) NOT NULL,document_type varchar(50) NOT NULL,content mediumtext NOT NULL,
 sort_order int NOT NULL DEFAULT 0,status char(1) NOT NULL DEFAULT '0',remark varchar(500),
 create_dept bigint,create_by bigint,create_time datetime,update_by bigint,update_time datetime,
 KEY idx_doc_template_type(tenant_id,document_type,status,sort_order,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
SET @knowledge=(SELECT menu_id FROM sys_menu WHERE parent_id=0 AND path='knowledge' LIMIT 1);
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon)
SELECT 2099101900000000300,'模板管理',@knowledge,40,'doc-template','legal/doc-template/index',1,0,'C','0','0','legal:doc-template:list','lucide:file-text'
WHERE @knowledge IS NOT NULL AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE menu_id=2099101900000000300 OR perms='legal:doc-template:list');
SET @templates=(SELECT menu_id FROM sys_menu WHERE perms='legal:doc-template:list' LIMIT 1);
UPDATE sys_menu SET parent_id=@knowledge,visible='0' WHERE menu_id=@templates AND @knowledge IS NOT NULL;
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,menu_type,visible,status,perms)
SELECT 2099101900000000301,'模板查询',@templates,1,'F','0','0','legal:doc-template:query' WHERE @templates IS NOT NULL;
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,menu_type,visible,status,perms)
SELECT 2099101900000000302,'模板新增',@templates,2,'F','0','0','legal:doc-template:add' WHERE @templates IS NOT NULL;
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,menu_type,visible,status,perms)
SELECT 2099101900000000303,'模板修改及启停',@templates,3,'F','0','0','legal:doc-template:edit' WHERE @templates IS NOT NULL;
INSERT IGNORE INTO sys_menu(menu_id,menu_name,parent_id,order_num,menu_type,visible,status,perms)
SELECT 2099101900000000304,'模板删除',@templates,4,'F','0','0','legal:doc-template:remove' WHERE @templates IS NOT NULL;
CREATE TABLE IF NOT EXISTS instruction25_template_role_added(role_id bigint,menu_id bigint,PRIMARY KEY(role_id,menu_id));
INSERT IGNORE INTO instruction25_template_role_added
SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON (m.menu_id=@knowledge OR m.menu_id=@templates OR m.parent_id=@templates)
LEFT JOIN sys_role_menu e ON e.role_id=r.role_id AND e.menu_id=m.menu_id
WHERE r.role_key IN ('admin','superadmin') AND r.del_flag='0' AND e.menu_id IS NULL;
INSERT INTO sys_role_menu(role_id,menu_id) SELECT a.role_id,a.menu_id FROM instruction25_template_role_added a
LEFT JOIN sys_role_menu e ON e.role_id=a.role_id AND e.menu_id=a.menu_id WHERE e.menu_id IS NULL;
-- For custom tenant packages: explicitly allow 模板管理 and its four buttons,
-- then sync the package. No new CRUD privilege is automatically added to packages.
-- Rollback: paired rollback SQL hides the menu and restores only added grants.
-- Keep legal_doc_template data. Back up before any later schema removal.
