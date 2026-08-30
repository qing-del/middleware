USE `personal_saas`;

-- Forward-only v0.4 document ownership and direct user authorization schema.
-- Keep team_id during the compatibility rollout; a later migration will retire it
-- after all document readers and writers use owner_user_id.
ALTER TABLE `biz_document`
    ADD COLUMN `owner_user_id` bigint NULL
        COMMENT '文档所有者用户ID'
        AFTER `id`;

UPDATE `biz_document`
SET `owner_user_id` = `team_id`
WHERE `owner_user_id` IS NULL;

ALTER TABLE `biz_document`
    MODIFY COLUMN `owner_user_id` bigint NOT NULL
        COMMENT '文档所有者用户ID',
    ADD KEY `idx_document_owner_deleted_time`
        (`owner_user_id`, `deleted`, `last_modify_time`);

CREATE TABLE `biz_document_user` (
    `document_id` bigint      NOT NULL COMMENT '关联 biz_document.id',
    `user_id`     bigint      NOT NULL COMMENT '被授权用户ID',
    `permission`  varchar(16) NOT NULL
        COMMENT 'READ 或 WRITE；WRITE 隐含 READ',
    `enabled`     tinyint     NOT NULL DEFAULT 1
        COMMENT '授权是否生效(0:已撤销,1:生效)',
    `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`document_id`, `user_id`),
    KEY `idx_document_user_visible`
        (`user_id`, `enabled`, `document_id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COMMENT='协作文档用户直接授权';
