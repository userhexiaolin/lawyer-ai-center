-- Restore the matching backend/frontend versions together. Keep type/template business data.
UPDATE sys_menu m JOIN template_menu_20261020_backup b ON b.menu_id=m.menu_id
SET m.parent_id=b.parent_id,m.path=b.path,m.menu_name=b.menu_name,m.order_num=b.order_num,m.visible=b.visible;
DELETE rm FROM sys_role_menu rm JOIN template_menu_20261020_role_added a ON a.role_id=rm.role_id AND a.menu_id=rm.menu_id;
UPDATE sys_tenant_package p JOIN template_menu_20261020_package_backup b ON b.package_id=p.package_id SET p.menu_ids=b.menu_ids;
DELETE m FROM sys_menu m JOIN template_menu_20261020_created c ON c.menu_id=m.menu_id;
-- Re-sync packages and re-login. Keep backups and legal_doc_template_type for recovery.
