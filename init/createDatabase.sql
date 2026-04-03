CREATE DATABASE IF NOT EXISTS `personal_saas` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `personal_saas`;

-- ==========================================
-- 1. 权限/角色表 (sys_role)
-- 满足需求：来一个权限表，以及对应的能调用api的次数上限
-- ==========================================
CREATE TABLE `sys_role` (
                            `id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
                            `role_name` varchar(50) NOT NULL COMMENT '角色名称(如：普通用户、VIP、管理员)',
                            `role_code` varchar(50) NOT NULL COMMENT '角色标识(如：USER, VIP, ADMIN)',
                            `daily_api_limit` int NOT NULL DEFAULT 5 COMMENT '该角色每日允许调用外部API的最高次数',
                            `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                            PRIMARY KEY (`id`),
                            UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色与额度配置表';

-- 初始化角色数据
INSERT INTO `sys_role` (`role_name`, `role_code`, `daily_api_limit`) VALUES
                                                                         ('创建者', 'CREATOR', 999999),
                                                                         ('管理员', 'ADMIN', 1000),
                                                                         ('普通用户', 'USER', 5),
                                                                         ('高级VIP', 'VIP', 50);


-- ==========================================
-- 2. 用户表 (sys_user)
-- 满足需求：用户要登录，密码采用加密存储，用户有权限等级
-- ==========================================
CREATE TABLE `sys_user` (
                            `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
                            `username` varchar(50) UNIQUE NOT NULL COMMENT '登录账号',
                            `password` char(60) NOT NULL COMMENT '加密密码(使用 BCrypt 加密存储)',
                            `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
                            `email` varchar(100) UNIQUE DEFAULT NULL COMMENT '邮箱地址(唯一)',
                            `role_id` bigint NOT NULL DEFAULT 3 COMMENT '关联的角色ID',
                            `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态(1:正常, 0:禁用)',
                            `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
                            `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                            PRIMARY KEY (`id`),
                            UNIQUE KEY `uk_username` (`username`),
                            UNIQUE KEY `idx_email` (`email`),
                            KEY `idx_nickname` (`nickname`, `status`),
                            KEY `idx_role_id` (`role_id`),
                            KEY `idx_status_role` (`status`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信息表';


-- ==========================================
-- 3. 主题/分类表 (biz_topic)
-- 满足需求：将笔记归类为MySQL主题的笔记，主题可以后续新增 (1对多)
-- ==========================================
CREATE TABLE `biz_topic` (
                             `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主题ID',
                             `user_id` bigint NOT NULL COMMENT '创建者ID(隔离不同用户的主题)',
                             `topic_name` varchar(100) NOT NULL COMMENT '主题名称(如: MySQL, Redis, 架构设计)',
                             `sort_order` int DEFAULT 0 COMMENT '排序字段(用于左侧菜单栏排序)',
                             `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                             PRIMARY KEY (`id`),
                             KEY `idx_user_id` (`user_id`),
                             UNIQUE KEY `uk_user_topic` (`user_id`, `topic_name`) -- 同一个用户不能创建同名的主题
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记主题/分类表';


-- ==========================================
-- 4. 标签表 (biz_tag)
-- 满足需求：类似obsidian中的标签，可被多篇笔记复用 (多对多)
-- ==========================================
CREATE TABLE `biz_tag` (
                           `id` bigint NOT NULL AUTO_INCREMENT COMMENT '标签ID',
                           `user_id` bigint NOT NULL COMMENT '创建者ID',
                           `tag_name` varchar(50) NOT NULL COMMENT '标签名称(如: 踩坑, 性能优化, 源码解析)',
                           `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           PRIMARY KEY (`id`),
                           KEY `idx_user_id` (`user_id`),
                           UNIQUE KEY `uk_user_tag` (`user_id`, `tag_name`) -- 同一个用户不能创建同名的标签
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记标签表';


-- ==========================================
-- 5. 笔记/文章主表 (biz_note)
-- 满足需求：关联主题ID，存储HTML路径
-- ==========================================
CREATE TABLE `biz_note` (
                            `id` bigint NOT NULL AUTO_INCREMENT COMMENT '笔记ID',
                            `user_id` bigint NOT NULL COMMENT '作者(用户ID)',
                            `topic_id` bigint DEFAULT NULL COMMENT '所属主题ID(允许为空，即未分类笔记)',
                            `title` varchar(255) NOT NULL COMMENT '笔记标题',
                            `html_file_path` varchar(500) NOT NULL COMMENT 'HTML文件在本地服务器的绝对/相对路径',
                            `md_file_path` varchar(500) DEFAULT NULL COMMENT '原始Markdown文件的存储路径(可选,便于后期编辑)',
                            `is_published` tinyint NOT NULL DEFAULT 0 COMMENT '是否发布(1:公开, 0:私密)',
                            `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                            PRIMARY KEY (`id`),
                            KEY `idx_user_id` (`user_id`),
                            KEY `idx_topic_id` (`topic_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记存储记录表';


-- ==========================================
-- 6. 笔记-标签 多对多关联表 (biz_note_tag_mapping)
-- 满足需求：一篇笔记对应多个标签，一个标签对应多篇笔记
-- ==========================================
CREATE TABLE `biz_note_tag_mapping` (
                                        `note_id` bigint NOT NULL COMMENT '笔记ID',
                                        `tag_id` bigint NOT NULL COMMENT '标签ID',
                                        `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关联时间',
                                        PRIMARY KEY (`note_id`, `tag_id`), -- 联合主键，防止重复绑定
                                        KEY `idx_tag_id` (`tag_id`) -- 方便通过 tag_id 反查所有 note_id
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记与标签多对多关联表';


-- ==========================================
-- 7. 图片资源映射表 (biz_image)
-- ==========================================
CREATE TABLE `biz_image` (
                             `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                             `user_id` bigint NOT NULL COMMENT '上传者ID',
                             `unique_filename` varchar(255) NOT NULL COMMENT '唯一文件名(例如: UUID.png)',
                             `oss_url` varchar(1000) NOT NULL COMMENT '图片在OSS(或云存储)中的完整访问URL',
                             `file_size` int DEFAULT NULL COMMENT '文件大小(字节, 选填)',
                             `upload_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
                             PRIMARY KEY (`id`),
                             UNIQUE KEY `uk_unique_filename` (`unique_filename`),
                             KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片资源映射表';


-- ==========================================
-- 8. 每日 API 调用统计表 (biz_api_daily_usage)
-- ==========================================
CREATE TABLE `biz_api_daily_usage` (
                                       `id` bigint NOT NULL AUTO_INCREMENT,
                                       `user_id` bigint NOT NULL COMMENT '用户ID',
                                       `record_date` date NOT NULL COMMENT '记录日期(只存年月日,如 2024-05-20)',
                                       `used_count` int NOT NULL DEFAULT 0 COMMENT '当日已调用次数',
                                       `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次调用时间',
                                       `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
                                       PRIMARY KEY (`id`),
                                       UNIQUE KEY `uk_user_date` (`user_id`, `record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户每日API调用次数统计表';


-- ==========================================
-- 9. API 异步任务明细表 (biz_api_task_log)
-- ==========================================
CREATE TABLE `biz_api_task_log` (
                                    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务/日志ID',
                                    `user_id` bigint NOT NULL COMMENT '调用者ID',
                                    `task_type` varchar(50) NOT NULL COMMENT '任务类型(如: generate_audio, analyze_text)',
                                    `task_status` tinyint NOT NULL DEFAULT 0 COMMENT '状态(0:处理中, 1:成功, 2:失败)',
                                    `result_url` varchar(1000) DEFAULT NULL COMMENT '如果执行成功，存放生成文件的下载地址',
                                    `error_msg` text DEFAULT NULL COMMENT '如果失败，记录 Python 端返回的错误信息',
                                    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '任务提交时间',
                                    `finish_time` datetime DEFAULT NULL COMMENT '任务完成时间',
                                    PRIMARY KEY (`id`),
                                    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Python脚本调用任务明细表';