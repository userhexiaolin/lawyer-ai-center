-- Restore exact original visibility, including pages that were already hidden.
UPDATE sys_menu m JOIN instruction25_hidden_menu_backup b ON b.menu_id=m.menu_id
SET m.visible=b.visible;
-- Backup intentionally retained. No menu or permission records are removed.
