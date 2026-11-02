-- Instruction 81. Before: chat -> provider/model/message/info/attach/fragment/aiflow/session.
-- After: models -> provider/model; knowledge -> info/attach/fragment;
-- chat (renamed 聊天管理) -> aiflow/message/session(hidden).
-- Absolute child paths preserve /chat/* URLs. Deploy frontend access.ts together.
-- Order: models(10), knowledge(20), chat(30), agent(40). AIFlow before messages.
CREATE TABLE IF NOT EXISTS instruction25_created_menu (menu_id bigint PRIMARY KEY);
CREATE TABLE IF NOT EXISTS instruction25_group_menu_backup LIKE sys_menu;
INSERT IGNORE INTO instruction25_group_menu_backup
SELECT * FROM sys_menu WHERE ((parent_id=0 AND (path IN ('chat','models','knowledge','agent','system','monitor','mcp','tenant') OR menu_name='对话管理'))
 OR component IN ('chat/provider/index','chat/model/index','chat/message/index','knowledge/info/index','knowledge/attach/index','knowledge/fragment/index','aiflow/index')
 OR (menu_type='C' AND path='session')) AND NOT EXISTS(SELECT 1 FROM instruction25_created_menu c WHERE c.menu_id=sys_menu.menu_id);
-- Exclude migration-created parents from snapshots even after repeated execution.
DELETE b FROM instruction25_group_menu_backup b JOIN instruction25_created_menu c ON c.menu_id=b.menu_id;
INSERT IGNORE INTO instruction25_created_menu SELECT 2099101900000000100 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE parent_id=0 AND path='models');
INSERT IGNORE INTO instruction25_created_menu SELECT 2099101900000000200 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE parent_id=0 AND path='knowledge');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,icon)
 SELECT 2099101900000000100,'模型管理',0,10,'models','',1,0,'M','0','0','lucide:boxes'
 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE parent_id=0 AND path='models');
INSERT INTO sys_menu(menu_id,menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,icon)
 SELECT 2099101900000000200,'知识管理',0,20,'knowledge','',1,0,'M','0','0','lucide:library'
 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE parent_id=0 AND path='knowledge');
SET @models=(SELECT menu_id FROM sys_menu WHERE parent_id=0 AND path='models' LIMIT 1);
SET @knowledge=(SELECT menu_id FROM sys_menu WHERE parent_id=0 AND path='knowledge' LIMIT 1);
SET @chat=(SELECT menu_id FROM sys_menu WHERE parent_id=0 AND path='chat' LIMIT 1);
UPDATE sys_menu SET parent_id=@models,path=CASE component WHEN 'chat/provider/index' THEN '/chat/provider' ELSE '/chat/model' END,
 order_num=CASE component WHEN 'chat/provider/index' THEN 10 ELSE 20 END
 WHERE component IN ('chat/provider/index','chat/model/index') AND @models IS NOT NULL;
UPDATE sys_menu SET parent_id=@knowledge,path=CASE component WHEN 'knowledge/info/index' THEN '/chat/info' WHEN 'knowledge/attach/index' THEN '/chat/attach' ELSE '/chat/fragment' END,
 order_num=CASE component WHEN 'knowledge/info/index' THEN 10 WHEN 'knowledge/attach/index' THEN 20 ELSE 30 END
 WHERE component IN ('knowledge/info/index','knowledge/attach/index','knowledge/fragment/index') AND @knowledge IS NOT NULL;
UPDATE sys_menu SET parent_id=@chat,order_num=CASE component WHEN 'aiflow/index' THEN 10 ELSE 20 END
 WHERE component IN ('aiflow/index','chat/message/index') AND @chat IS NOT NULL;
UPDATE sys_menu SET parent_id=@chat WHERE menu_type='C' AND path='session' AND @chat IS NOT NULL;
UPDATE sys_menu SET menu_name='聊天管理',order_num=30 WHERE menu_id=@chat;
UPDATE sys_menu SET menu_name='模型管理',order_num=10 WHERE menu_id=@models;
UPDATE sys_menu SET menu_name='知识管理',order_num=20 WHERE menu_id=@knowledge;
UPDATE sys_menu SET order_num=40 WHERE parent_id=0 AND path='agent';
UPDATE sys_menu SET order_num=CASE path WHEN 'mcp' THEN 50 WHEN 'system' THEN 100 WHEN 'monitor' THEN 110 WHEN 'tenant' THEN 120 END
WHERE parent_id=0 AND path IN ('mcp','system','monitor','tenant');
-- Grant only newly needed parents to roles already authorized for children.
CREATE TABLE IF NOT EXISTS instruction25_group_role_added (role_id bigint,menu_id bigint,PRIMARY KEY(role_id,menu_id));
INSERT IGNORE INTO instruction25_group_role_added
SELECT DISTINCT rm.role_id,m.parent_id FROM sys_role_menu rm JOIN sys_menu m ON m.menu_id=rm.menu_id
LEFT JOIN sys_role_menu existing ON existing.role_id=rm.role_id AND existing.menu_id=m.parent_id
WHERE m.parent_id IN (@models,@knowledge) AND existing.menu_id IS NULL;
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT a.role_id,a.menu_id FROM instruction25_group_role_added a
LEFT JOIN sys_role_menu e ON e.role_id=a.role_id AND e.menu_id=a.menu_id WHERE e.menu_id IS NULL;
-- Tenant package menu_ids is CSV (not JSON). Append parents only when an existing
-- child is allowed. No new leaf/button privilege is granted. Re-sync packages in
-- tenant administration after rollout and review customized packages manually.
CREATE TABLE IF NOT EXISTS instruction25_group_package_backup LIKE sys_tenant_package;
INSERT IGNORE INTO instruction25_group_package_backup SELECT * FROM sys_tenant_package;
UPDATE sys_tenant_package p SET menu_ids=CONCAT_WS(',',NULLIF(p.menu_ids,''),CAST(@models AS CHAR))
WHERE @models IS NOT NULL AND NOT FIND_IN_SET(@models,COALESCE(p.menu_ids,''))
AND EXISTS(SELECT 1 FROM sys_menu m WHERE m.parent_id=@models AND FIND_IN_SET(m.menu_id,p.menu_ids));
UPDATE sys_tenant_package p SET menu_ids=CONCAT_WS(',',NULLIF(p.menu_ids,''),CAST(@knowledge AS CHAR))
WHERE @knowledge IS NOT NULL AND NOT FIND_IN_SET(@knowledge,COALESCE(p.menu_ids,''))
AND EXISTS(SELECT 1 FROM sys_menu m WHERE m.parent_id=@knowledge AND FIND_IN_SET(m.menu_id,p.menu_ids));
-- Rollback: paired rollback SQL. Re-login to refresh menus.
