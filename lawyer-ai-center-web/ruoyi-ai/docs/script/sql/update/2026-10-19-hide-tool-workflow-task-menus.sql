-- Instruction 80. MySQL 8+, application database. Local deployments do not need
-- developer tools, Warm-Flow or task menus. Keep records, grants and F permissions.
-- Re-runnable: INSERT IGNORE preserves the original visibility for exact rollback.
CREATE TABLE IF NOT EXISTS instruction25_hidden_menu_backup (
  menu_id bigint NOT NULL PRIMARY KEY,
  visible char(1)
) ENGINE=InnoDB;
INSERT IGNORE INTO instruction25_hidden_menu_backup(menu_id, visible)
WITH RECURSIVE hidden_tree AS (
  SELECT menu_id, visible FROM sys_menu
  WHERE menu_type='M' AND parent_id=0
    AND (TRIM(BOTH '/' FROM path) IN ('tool','workflow','task')
      OR menu_name IN ('系统工具','工作流','我的任务'))
  UNION ALL
  SELECT m.menu_id, m.visible FROM sys_menu m
  JOIN hidden_tree p ON m.parent_id=p.menu_id
)
SELECT m.menu_id, m.visible FROM sys_menu m JOIN hidden_tree t ON t.menu_id=m.menu_id
WHERE m.menu_type IN ('M','C');
UPDATE sys_menu m JOIN instruction25_hidden_menu_backup b ON b.menu_id=m.menu_id
SET m.visible='1' WHERE m.visible IS NULL OR m.visible<>'1';
-- Rollback: execute 2026-10-19-hide-tool-workflow-task-menus-rollback.sql.
-- Log out/in after execution to refresh the administrator's cached menu tree.
