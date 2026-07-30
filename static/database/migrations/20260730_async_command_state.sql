CREATE TABLE IF NOT EXISTS `sys_async_command_state` (
    `id`             bigint       NOT NULL AUTO_INCREMENT,
    `owner_module`   varchar(32)  NOT NULL,
    `aggregate_type` varchar(64)  NOT NULL,
    `aggregate_id`   bigint       NOT NULL,
    `command_id`     varchar(64)  NOT NULL,
    `command_type`   varchar(64)  NOT NULL,
    `state`          varchar(16)  NOT NULL,
    `update_time`    datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_async_command_aggregate` (`owner_module`, `aggregate_type`, `aggregate_id`),
    KEY `idx_async_command_id` (`command_id`),
    KEY `idx_async_command_state_time` (`state`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Current asynchronous command correlation state';
