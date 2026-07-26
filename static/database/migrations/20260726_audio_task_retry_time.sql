ALTER TABLE `audio_tasks`
    ADD COLUMN `retry_time` int NOT NULL DEFAULT 0 COMMENT '超时重试次数' AFTER `status`;
