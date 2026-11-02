-- Restores only backed-up M/C display fields; never alters F or role grants.
UPDATE sys_menu m JOIN instruction24_menu_backup b ON b.menu_id=m.menu_id
 SET m.visible=b.visible,m.order_num=b.order_num WHERE m.menu_type IN ('M','C');
-- Keep backup table for audit. Re-running forward SQL after this rollback is supported.
