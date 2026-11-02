-- 75: Run in the application database. No F/menu authorization rows are removed.
CREATE TABLE IF NOT EXISTS instruction24_menu_backup (
 menu_id bigint NOT NULL PRIMARY KEY, visible char(1), order_num int
) ENGINE=InnoDB;
INSERT IGNORE INTO instruction24_menu_backup(menu_id,visible,order_num)
 SELECT menu_id,visible,order_num FROM sys_menu
 WHERE menu_type IN ('M','C') AND
 (component IN ('chat/provider/index','chat/model/index','knowledge/info/index','knowledge/attach/index','knowledge/fragment/index','aiflow/index','chat/message/index','monitor/admin/index','monitor/snailjob/index') OR component LIKE 'graph/%');
UPDATE sys_menu SET order_num=CASE component
 WHEN 'chat/provider/index' THEN 10 WHEN 'chat/model/index' THEN 20
 WHEN 'knowledge/info/index' THEN 30 WHEN 'knowledge/attach/index' THEN 31 WHEN 'knowledge/fragment/index' THEN 32
 WHEN 'aiflow/index' THEN 40 WHEN 'chat/message/index' THEN 50 ELSE order_num END
 WHERE menu_type IN ('M','C') AND component IN ('chat/provider/index','chat/model/index','knowledge/info/index','knowledge/attach/index','knowledge/fragment/index','aiflow/index','chat/message/index');
UPDATE sys_menu SET visible='1' WHERE menu_type IN ('M','C') AND
 (component IN ('monitor/admin/index','monitor/snailjob/index') OR component LIKE 'graph/%');
-- Existing tenant packages keep their menuIds and button permissions. Generator visibility is
-- filtered by super-admin status in SysMenuServiceImpl and enforced by GenController.
-- Rollback visible/order: execute 2026-10-19-lawyer-menu-rollback.sql, then restore frontend/backend binaries.
