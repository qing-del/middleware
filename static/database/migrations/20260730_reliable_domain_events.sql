-- Reliable at-least-once domain event delivery.
CREATE TABLE IF NOT EXISTS `sys_event_outbox` (
    `id`              bigint       NOT NULL AUTO_INCREMENT,
    `event_id`        varchar(64)  NOT NULL,
    `event_type`      varchar(128) NOT NULL,
    `routing_key`     varchar(128) NOT NULL,
    `payload`         longtext     NOT NULL,
    `status`          varchar(16)  NOT NULL DEFAULT 'PENDING',
    `retry_count`     int          NOT NULL DEFAULT 0,
    `next_retry_time` datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `last_error`      varchar(2000) DEFAULT NULL,
    `claimed_by`      varchar(64)  DEFAULT NULL,
    `claim_until`     datetime(3)  DEFAULT NULL,
    `create_time`     datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `published_time`  datetime(3)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_event_outbox_event_id` (`event_id`),
    KEY `idx_event_outbox_ready` (`status`, `next_retry_time`, `id`),
    KEY `idx_event_outbox_claim` (`status`, `claim_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Transactional domain event outbox';

CREATE TABLE IF NOT EXISTS `sys_event_inbox` (
    `id`            bigint       NOT NULL AUTO_INCREMENT,
    `event_id`      varchar(64)  NOT NULL,
    `consumer_name` varchar(128) NOT NULL,
    `event_type`    varchar(128) NOT NULL,
    `status`        varchar(16)  NOT NULL,
    `last_error`    varchar(2000) DEFAULT NULL,
    `create_time`   datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `consumed_time` datetime(3)  DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_event_inbox_consumer` (`event_id`, `consumer_name`),
    KEY `idx_event_inbox_consumer_time` (`consumer_name`, `consumed_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Idempotent domain event consumption records';

CREATE TABLE IF NOT EXISTS `sys_event_projection_version` (
    `id`             bigint       NOT NULL AUTO_INCREMENT,
    `consumer_name`  varchar(128) NOT NULL,
    `aggregate_type` varchar(64)  NOT NULL,
    `aggregate_id`   bigint       NOT NULL,
    `last_sequence`  bigint       NOT NULL,
    `update_time`    datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_event_projection_aggregate`
        (`consumer_name`, `aggregate_type`, `aggregate_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Last applied sequence for ordered event projections';
