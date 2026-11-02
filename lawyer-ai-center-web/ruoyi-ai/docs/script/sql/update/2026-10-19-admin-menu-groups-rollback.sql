UPDATE sys_menu m JOIN instruction25_group_menu_backup b ON b.menu_id=m.menu_id
SET m.parent_id=b.parent_id,m.path=b.path,m.menu_name=b.menu_name,m.order_num=b.order_num,m.visible=b.visible;
DELETE rm FROM sys_role_menu rm JOIN instruction25_group_role_added a ON a.role_id=rm.role_id AND a.menu_id=rm.menu_id;
UPDATE sys_tenant_package p JOIN instruction25_group_package_backup b ON b.package_id=p.package_id SET p.menu_ids=b.menu_ids;
DELETE m FROM sys_menu m JOIN instruction25_created_menu c ON c.menu_id=m.menu_id
WHERE m.path IN ('models','knowledge') AND NOT EXISTS(SELECT 1 FROM instruction25_group_menu_backup b WHERE b.menu_id=m.menu_id);
-- Restore the matching frontend release. Backups remain for audit.
