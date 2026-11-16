-- 任务说明（与笔记 note 分开）：给守卫判定提供任务背景，不出现在悬浮窗随手笔记里。
-- 未应用到线上库：须 Ti 确认后再执行。
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS description text;
