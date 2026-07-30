CREATE TABLE IF NOT EXISTS `audit_query_user_projection` (
    `user_id`     bigint       NOT NULL,
    `username`    varchar(128) NOT NULL,
    `nickname`    varchar(128) DEFAULT NULL,
    `update_time` datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Audit-owned current user projection used when taking snapshots';

CREATE TABLE IF NOT EXISTS `audit_query_record_projection` (
    `target_type`        varchar(16)   NOT NULL,
    `audit_id`           bigint        NOT NULL,
    `target_id`          bigint        NOT NULL,
    `applicant_username` varchar(128)  DEFAULT NULL,
    `reviewer_username`  varchar(128)  DEFAULT NULL,
    `target_name`        varchar(500)  DEFAULT NULL,
    `target_url`         varchar(1000) DEFAULT NULL,
    `update_time`        datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`target_type`, `audit_id`),
    KEY `idx_audit_query_record_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable display snapshot per audit record';

-- One-time compatibility backfill. Runtime audit queries never join owner-module tables.
INSERT INTO audit_query_user_projection (user_id, username, nickname)
SELECT id, username, nickname FROM sys_user
ON DUPLICATE KEY UPDATE username=VALUES(username), nickname=VALUES(nickname), update_time=NOW();

INSERT INTO audit_query_record_projection
    (target_type, audit_id, target_id, applicant_username, reviewer_username, target_name, target_url)
SELECT 'NOTE', audit.id, audit.note_id, applicant.username, reviewer.username, note.title, NULL
FROM biz_note_audit_record audit
LEFT JOIN sys_user applicant ON applicant.id = audit.applicant_user_id
LEFT JOIN sys_user reviewer ON reviewer.id = audit.reviewer_user_id
LEFT JOIN biz_note note ON note.id = audit.note_id
UNION ALL
SELECT 'TAG', audit.id, audit.target_id, applicant.username, reviewer.username, tag.tag_name, NULL
FROM biz_tag_audit_record audit
LEFT JOIN sys_user applicant ON applicant.id = audit.applicant_user_id
LEFT JOIN sys_user reviewer ON reviewer.id = audit.reviewer_user_id
LEFT JOIN biz_tag tag ON tag.id = audit.target_id
WHERE audit.apply_type = 2
UNION ALL
SELECT 'IMAGE', audit.id, audit.image_id, applicant.username, reviewer.username, image.filename, image.oss_url
FROM biz_image_audit_record audit
LEFT JOIN sys_user applicant ON applicant.id = audit.applicant_user_id
LEFT JOIN sys_user reviewer ON reviewer.id = audit.reviewer_user_id
LEFT JOIN biz_image image ON image.id = audit.image_id
ON DUPLICATE KEY UPDATE audit_id=VALUES(audit_id);
