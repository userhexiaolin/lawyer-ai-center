-- 与律行品牌升级：停用初始化时提供的上游示例快捷链接。
-- 可重复执行；保留记录，不将第三方网站冒充为与律行官网。
-- 新安装的全量 SQL 已默认停用这两条示例链接；已有数据库执行本脚本。
UPDATE sys_url
SET status = '1', update_time = NOW()
WHERE tenant_id = '000000'
  AND remark = '初始公开链接'
  AND status = '0'
  AND (
    (name = '若依官网' AND url = 'https://ruoyi.vip')
    OR (name = 'RuoYi-AI 开源仓库' AND url = 'https://github.com/ageerle/ruoyi-ai')
  );
