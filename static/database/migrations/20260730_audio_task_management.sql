-- Audio task management and storage accounting upgrade.
ALTER TABLE `audio_tasks`
    ADD COLUMN `audio_size` bigint DEFAULT NULL COMMENT '成功后音频文件大小（字节）' AFTER `result_url`;

ALTER TABLE `audio_tasks`
    MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 0
        COMMENT '状态：0-PENDING，1-PROCESSING，2-SUCCESS，-1-FAILED，-2-RETRIED，-3-CANCELLED';
