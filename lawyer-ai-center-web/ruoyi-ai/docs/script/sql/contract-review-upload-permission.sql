-- Default-tenant ordinary users may upload contracts; preserve existing permissions.
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, m.menu_id
FROM sys_role r
JOIN sys_menu m ON m.perms = 'system:oss:upload'
WHERE r.role_key = 'user' AND r.tenant_id = '000000'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_menu rm
    WHERE rm.role_id = r.role_id AND rm.menu_id = m.menu_id
  );
-- Re-login after applying so the user's cached permissions are refreshed.
