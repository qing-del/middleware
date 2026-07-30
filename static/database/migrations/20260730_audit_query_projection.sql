CREATE TABLE IF NOT EXISTS `audit_query_user_projection` (
    `user_id`     bigint       NOT NULL,
    `username`    varchar(128) NOT NULL,
    `nickname`    varchar(128) DEFAULT NULL,
    `update_time` datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Audit-owned user display projection';

CREATE TABLE IF NOT EXISTS `audit_query_subject_projection` (
    `target_type` varchar(16)   NOT NULL,
    `target_id`   bigint        NOT NULL,
    `target_name` varchar(500)  NOT NULL,
    `target_url`  varchar(1000) DEFAULT NULL,
    `update_time` datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Audit-owned target display projection';

-- One-time compatibility backfill. Runtime audit queries never join owner-module tables.
INSERT INTO audit_query_user_projection (user_id, username, nickname)
SELECT id, username, nickname FROM sys_user
ON DUPLICATE KEY UPDATE username=VALUES(username), nickname=VALUES(nickname), update_time=NOW();

INSERT INTO audit_query_subject_projection (target_type, target_id, target_name, target_url)
SELECT 'NOTE', id, title, NULL FROM biz_note
UNION ALL SELECT 'TAG', id, tag_name, NULL FROM biz_tag
UNION ALL SELECT 'IMAGE', id, filename, oss_url FROM biz_image
ON DUPLICATE KEY UPDATE target_name=VALUES(target_name), target_url=VALUES(target_url), update_time=NOW();
