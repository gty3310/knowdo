-- 每周重复任务的星期选择（ISO：1=周一 … 7=周日，逗号分隔，如 '1,2,3,4,5'）。
-- 仅当 tasks.repeat = 'weekly' 时有意义。
-- 未应用到线上库：须 Ti 确认后再执行。
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS repeat_weekdays text;
