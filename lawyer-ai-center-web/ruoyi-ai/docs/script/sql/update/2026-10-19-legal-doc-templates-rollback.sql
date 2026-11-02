-- Move the hidden leaf under the original chat root so reversing instruction 81
-- afterwards cannot leave an orphan under a removed knowledge directory.
UPDATE sys_menu SET visible='1',parent_id=COALESCE((SELECT root.menu_id FROM (SELECT menu_id FROM sys_menu WHERE parent_id=0 AND path='chat' LIMIT 1) root),parent_id)
WHERE perms='legal:doc-template:list';
DELETE rm FROM sys_role_menu rm JOIN instruction25_template_role_added a ON a.role_id=rm.role_id AND a.menu_id=rm.menu_id;
-- Restore previous application binaries. Template table, records and permissions
-- remain recoverable. Do not drop customer templates to roll back a release.
