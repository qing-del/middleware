-- Synchronous audit submit/cancel migration.
ALTER TABLE `biz_note_audit_record`
    MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 0
    COMMENT 'audit status: 0 pending, 1 approved, 2 rejected, 3 cancelled';

ALTER TABLE `biz_tag_audit_record`
    MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 0
    COMMENT 'audit status: 0 waiting, 1 auditing, 2 approved, 3 rejected, 4 deleted, 5 cancelled';

ALTER TABLE `biz_image_audit_record`
    MODIFY COLUMN `status` tinyint NOT NULL DEFAULT 0
    COMMENT 'audit status: 0 waiting, 1 auditing, 2 approved, 3 rejected, 4 deleted, 5 cancelled';

UPDATE `biz_tag_audit_record` SET `status` = 5 WHERE `status` = 0;
UPDATE `biz_image_audit_record` SET `status` = 5 WHERE `status` = 0;

DROP TABLE IF EXISTS `sys_async_command_state`;
