-- Preserve the legacy physical-delete table as an observable migration/tracking ledger.
-- Statuses: 0 legacy waiting, 1 completed, 2 queued through Outbox, 3 terminal failure.
ALTER TABLE `biz_image_delete_dead_letter`
    ADD COLUMN `resource_id` varchar(64) DEFAULT NULL AFTER `id`,
    ADD COLUMN `event_id` varchar(64) DEFAULT NULL AFTER `image_url`,
    ADD COLUMN `last_error` varchar(500) DEFAULT NULL AFTER `retry_count`,
    ADD COLUMN `completed_time` datetime DEFAULT NULL AFTER `update_time`,
    ADD KEY `idx_delete_event` (`event_id`),
    ADD KEY `idx_delete_resource` (`resource_id`);
