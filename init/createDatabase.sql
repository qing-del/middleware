CREATE DATABASE IF NOT EXISTS `personal_saas` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `personal_saas`;

-- ==========================================
-- 1. 权限/角色表 (sys_role)
-- 满足需求：来一个权限表，以及对应的能调用api的次数上限、存储空间上限
-- ==========================================
CREATE TABLE `sys_role` (
                            `id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
                            `role_name` varchar(50) NOT NULL COMMENT '角色名称(如：普通用户、VIP、管理员)',
                            `role_code` varchar(50) NOT NULL COMMENT '角色标识(如：USER, VIP, ADMIN)',
                            `daily_api_limit` int NOT NULL DEFAULT 5 COMMENT '该角色每日允许调用外部API的最高次数',
                            `max_storage_bytes` bigint NOT NULL DEFAULT 104857600 COMMENT '该角色默认最大存储空间(字节, 默认100MB=104857600)',
                            `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                            PRIMARY KEY (`id`),
                            UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色与额度配置表';

-- 初始化角色数据 (max_storage_bytes: CREATOR=100GB, ADMIN=10GB, USER=100MB, VIP=1GB)
INSERT INTO `sys_role` (`role_name`, `role_code`, `daily_api_limit`, `max_storage_bytes`) VALUES
                                                                         ('创建者', 'CREATOR', 999999, 107374182400),
                                                                         ('管理员', 'ADMIN', 1000, 10737418240),
                                                                         ('普通用户', 'USER', 5, 104857600),
                                                                         ('高级VIP', 'VIP', 50, 1073741824);


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
                            `max_storage_bytes` bigint DEFAULT NULL COMMENT '用户个性化最大存储空间(字节, NULL=使用角色默认值, 管理员可单独设置)',
                            `used_storage_bytes` bigint NOT NULL DEFAULT 0 COMMENT '用户当前已用存储空间(字节, 笔记与图片合计)',
                            `status` tinyint NOT NULL DEFAULT 2 COMMENT '状态(2:未激活, 1:正常, 0:禁用)',
                            `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
                            `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                            PRIMARY KEY (`id`),
                            UNIQUE KEY `uk_username` (`username`),
                            UNIQUE KEY `idx_email` (`email`),
                            KEY `idx_nickname` (`nickname`, `status`),
                            -- KEY `idx_role_id` (`role_id`), “废索引”
                            KEY `idx_status_role` (`status`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信息表';
-- 服务器启动时会默认创建（若存在则会进行覆盖该用户）一个id=1的创建者用户


-- ==========================================
-- 3. 主题/分类表 (biz_topic)
-- 满足需求：将笔记归类为MySQL主题的笔记，主题可以后续新增 (1对多)
-- ==========================================
CREATE TABLE `biz_topic` (
                             `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主题ID',
                             `user_id` bigint NOT NULL COMMENT '创建者ID(隔离不同用户的主题)',
                             `topic_name` varchar(25) NOT NULL COMMENT '主题名称(如: MySQL, Redis, 架构设计)',
                             `sort_order` int DEFAULT 0 COMMENT '排序字段(用于左侧菜单栏排序)',
                             `is_pass` tinyint NOT NULL DEFAULT 0 COMMENT '审核状态(0:待审核, 1:已通过, 2:已拒绝)',
                             `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                             PRIMARY KEY (`id`),
                             KEY `idx_user_id` (`user_id`),
                             KEY `idx_user_sort_update` (`user_id`, `sort_order`, `update_time`),
                             UNIQUE KEY `uk_topic_user` (`topic_name`, `user_id`) -- 同一个用户不能创建同名的主题
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记主题/分类表';


-- ==========================================
-- 4. 标签表 (biz_tag)
-- 满足需求：类似obsidian中的标签，可被多篇笔记复用 (多对多)
-- ==========================================
CREATE TABLE `biz_tag` (
                           `id` bigint NOT NULL AUTO_INCREMENT COMMENT '标签ID',
                           `user_id` bigint NOT NULL COMMENT '创建者ID',
                           `tag_name` varchar(20) NOT NULL COMMENT '标签名称(如: 踩坑, 性能优化, 源码解析)',
                           `is_pass` tinyint NOT NULL DEFAULT 0 COMMENT '审核状态(0:待审核, 1:已通过, 2:已拒绝)',
                           `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           PRIMARY KEY (`id`),
                           KEY `idx_user_id` (`user_id`),
                           UNIQUE KEY `uk_tag_user` (`tag_name`, `user_id`) -- 同一个用户不能创建同名的标签
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记标签表';


-- ==========================================
-- 5. 笔记/文章主表 (biz_note)
-- 满足需求：关联主题ID，存储HTML路径
-- ==========================================
CREATE TABLE `biz_note` (
                            `id` bigint NOT NULL AUTO_INCREMENT COMMENT '笔记ID',
                            `user_id` bigint NOT NULL COMMENT '作者(用户ID)',
                            `topic_id` bigint DEFAULT NULL COMMENT '所属主题ID(允许为空，即未分类笔记)',
                            `title` varchar(100) NOT NULL COMMENT '笔记标题',
                            `description` varchar(255) DEFAULT NULL COMMENT '笔记描述',
                            `html_file_path` varchar(500) NOT NULL COMMENT 'HTML文件在本地服务器的绝对/相对路径',
                            `md_file_path` varchar(500) DEFAULT NULL COMMENT '原始Markdown文件的存储路径',
                            `is_published` tinyint NOT NULL DEFAULT 0 COMMENT '是否发布(1:公开, 0:私密)',
                            `storage_type` tinyint NOT NULL COMMENT '存储方式-0:本地存储, 1:阿里云OSS, 2:Cloudflare R2(预留)',
                            `is_missing_photo` tinyint NOT NULL DEFAULT 0 COMMENT '是否缺少图片(0:正常, 1:缺少图片)',
                            `is_pass` tinyint NOT NULL DEFAULT 0 COMMENT '审核状态(0:待审核, 1:已通过, 2:已拒绝)',
                            `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除(1:删除, 0:正常)',
                            `md_file_size` bigint NOT NULL DEFAULT 0 COMMENT 'MD文件大小合计(字节, 上传时由服务端写入, 用于存储配额统计)',
                            `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                            PRIMARY KEY (`id`),
                            KEY `idx_user_id` (`user_id`),
                            KEY `idx_topic_deleted` (`topic_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记存储记录表';


-- ==========================================
-- 6. 笔记-标签 多对多关联表 (biz_note_tag_mapping)
-- 满足需求：一篇笔记对应多个标签，一个标签对应多篇笔记
-- ==========================================
CREATE TABLE `biz_note_tag_mapping` (
                                        `note_id` bigint NOT NULL COMMENT '笔记ID',
                                        `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除(1:删除, 0:正常)',
                                        `tag_id` bigint NOT NULL COMMENT '标签ID',
                                        `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关联时间',
                                        PRIMARY KEY (`note_id`, `tag_id`), -- 联合主键，防止重复绑定
                                        KEY `idx_tag_deleted` (`tag_id`, `is_deleted`) -- 方便通过 tag_id 反查所有 note_id
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记与标签多对多关联表';


-- ==========================================
-- 7. 图片资源映射表 (biz_image)
-- ==========================================
CREATE TABLE `biz_image` (
                             `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                             `user_id` bigint NOT NULL COMMENT '上传者ID',
                             `topic_id` bigint DEFAULT NULL COMMENT '所属主题ID,0-默认(未分类)',
                             `filename` varchar(255) NOT NULL COMMENT '文件名',
                             `oss_url` varchar(1000) NOT NULL COMMENT '图片在OSS(或云存储)中的完整访问URL',
                             `storage_type` tinyint NOT NULL COMMENT '存储方式-1:阿里云OSS, 2:Cloudflare R2(预留)',
                             `file_size` int DEFAULT NULL COMMENT '文件大小(字节, 选填)',
                             `is_public` tinyint NOT NULL DEFAULT 0 COMMENT '是否公开(0:私有仅本人可引用, 1:公开可被其他用户搜索复用)',
                             `is_pass` tinyint NOT NULL DEFAULT 0 COMMENT '审核状态(0:待审核, 1:已通过, 2:已拒绝)',
                             `upload_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
                             PRIMARY KEY (`id`),
                             -- 对于 (uk_user_topic_filename) 采取 Java 层逻辑唯一索引
                             KEY `uk_user_topic_filename` (`user_id`, `topic_id`, filename(40)), -- 用户查询自己有哪些图片可以使用
                             KEY `idx_topic_public_filename` (`topic_id`, `is_public`, filename(40)), -- 用于查询某个主题下的所有图片(同时支持前缀模糊查询文件名)
                             KEY `idx_public_filename` (`is_public`, filename(40)) -- 用于公共图片库按文件名搜索
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片资源映射表';


-- ==========================================
-- 8. 笔记-图片引用映射表 (biz_note_image_mapping)
-- 满足需求：笔记解析时通过图片文件名查找图片URL，支持跨用户图片复用节省存储
-- ==========================================
CREATE TABLE `biz_note_image_mapping` (
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `note_id`           bigint       NOT NULL COMMENT '关联的笔记ID',
    `image_id`          bigint       NOT NULL COMMENT '关联的图片ID(biz_image.id)',
    `note_user_id`      bigint       NOT NULL COMMENT '笔记所属用户ID(冗余字段，避免JOIN查询)',
    `image_user_id`     bigint       NOT NULL COMMENT '图片所属用户ID(冗余字段，标识是否跨用户引用)',
    `parsed_image_name` varchar(255) NOT NULL COMMENT '笔记中解析出的原始图片名称(如: 架构图.png)，用于建立名称到URL的映射',
    `note_title`        varchar(100) NOT NULL COMMENT '笔记标题(冗余字段，用于后续按笔记标题搜索图片)',
    `is_cross_user`     tinyint      NOT NULL DEFAULT 0 COMMENT '是否跨用户引用(0:同一用户, 1:引用了其他用户的公开图片)',
    `is_deleted`        tinyint      NOT NULL DEFAULT 0 COMMENT '是否已删除(0:正常, 1:已删除，删除时软删)',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '映射创建时间(首次解析时间)',
    `update_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_note_image` (`note_id`, `image_id`),                          -- 同一笔记不重复绑定同一张图片
    KEY `idx_note_id` (`note_id`, `is_deleted`),                                 -- 按笔记查所有引用图片(渲染时用)
    KEY `idx_image_id` (`image_id`, `is_deleted`),                               -- 按图片反查所有引用笔记(删图前检查用)
    KEY `idx_note_user_name` (`note_user_id`, `parsed_image_name`, `is_deleted`) -- 解析时按用户+文件名快速定位已有映射
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记-图片引用映射表';


-- ==========================================
-- 9. 每日 API 调用统计表 (biz_api_daily_usage)
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
-- 10. API 异步任务明细表 (biz_api_task_log)
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


-- ==========================================
-- 11. 元数据审核表 (biz_meta_audit_record)  —  主题 & 标签
-- 满足需求：主题和标签均属于轻量元数据，量级相近，合用一张审核表
-- apply_type: 1=主题, 2=标签
-- ==========================================
CREATE TABLE `biz_meta_audit_record` (
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '审核记录ID',
    `applicant_user_id` bigint       NOT NULL COMMENT '申请者用户ID',
    `apply_type`        tinyint      NOT NULL COMMENT '申请类型(1:主题, 2:标签)',
    `target_id`         bigint       NOT NULL COMMENT '关联的主题ID或标签ID(由apply_type决定)',
    `apply_reason`      varchar(500) DEFAULT NULL COMMENT '申请说明(可选)',
    `status`            tinyint      NOT NULL DEFAULT 0 COMMENT '审核状态(0:待审核, 1:已通过, 2:已拒绝)',
    `reviewer_user_id`  bigint       DEFAULT NULL COMMENT '审核员用户ID(NULL=尚未审核)',
    `reject_reason`     varchar(500) DEFAULT NULL COMMENT '拒绝原因(status=2时填写)',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请提交时间',
    `review_time`       datetime     DEFAULT NULL COMMENT '审核完成时间',
    PRIMARY KEY (`id`),
    KEY `idx_applicant_status` (`applicant_user_id`, `status`), -- 用户查自己的申请
    KEY `idx_status_type`      (`status`, `apply_type`),        -- 管理员批量处理
    KEY `idx_target`           (`apply_type`, `target_id`)      -- 按类型+目标ID反查审核记录
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='主题&标签元数据审核表';

-- ==========================================
-- 12. 图片审核表 (biz_image_audit_record)
-- ==========================================
CREATE TABLE `biz_image_audit_record` (
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '审核记录ID',
    `applicant_user_id` bigint       NOT NULL COMMENT '申请者用户ID',
    `image_id`          bigint       NOT NULL COMMENT '关联的图片ID(biz_image.id)',
    `apply_reason`      varchar(500) DEFAULT NULL COMMENT '申请说明(可选)',
    `status`            tinyint      NOT NULL DEFAULT 0 COMMENT '审核状态(0:待审核, 1:已通过, 2:已拒绝)',
    `reviewer_user_id`  bigint       DEFAULT NULL COMMENT '审核员用户ID(NULL=尚未审核)',
    `reject_reason`     varchar(500) DEFAULT NULL COMMENT '拒绝原因(status=2时填写)',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请提交时间',
    `review_time`       datetime     DEFAULT NULL COMMENT '审核完成时间',
    PRIMARY KEY (`id`),
    KEY `idx_applicant_status` (`applicant_user_id`, `status`), -- 用户查自己的申请
    KEY `idx_status`           (`status`),                      -- 管理员批量拉待审队列
    KEY `idx_image_id`         (`image_id`)                     -- 按图片反查审核记录
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片审核表';

-- ==========================================
-- 13. 笔记审核表 (biz_note_audit_record)
-- ==========================================
CREATE TABLE `biz_note_audit_record` (
    `id`                bigint       NOT NULL AUTO_INCREMENT COMMENT '审核记录ID',
    `applicant_user_id` bigint       NOT NULL COMMENT '申请者用户ID',
    `note_id`           bigint       NOT NULL COMMENT '关联的笔记ID(biz_note.id)',
    `apply_reason`      varchar(500) DEFAULT NULL COMMENT '申请说明(可选)',
    `status`            tinyint      NOT NULL DEFAULT 0 COMMENT '审核状态(0:待审核, 1:已通过, 2:已拒绝)',
    `reviewer_user_id`  bigint       DEFAULT NULL COMMENT '审核员用户ID(NULL=尚未审核)',
    `reject_reason`     varchar(500) DEFAULT NULL COMMENT '拒绝原因(status=2时填写)',
    `create_time`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请提交时间',
    `review_time`       datetime     DEFAULT NULL COMMENT '审核完成时间',
    PRIMARY KEY (`id`),
    KEY `idx_applicant_status` (`applicant_user_id`, `status`), -- 用户查自己的申请
    KEY `idx_status`           (`status`),                      -- 管理员批量拉待审队列
    KEY `idx_note_id`          (`note_id`)                      -- 按笔记反查审核记录
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记审核表';


-- ==========================================
-- 14. 图片删除死信队列表 (biz_image_delete_dead_letter)
-- 用于记录需要异步重试删除的图片URL
-- status: 0=等待删除, 1=删除完成
-- ==========================================
CREATE TABLE `biz_image_delete_dead_letter` (
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `image_url`    varchar(1000) NOT NULL COMMENT '待删除图片URL(OSS/R2等完整地址)',
    `status`       tinyint      NOT NULL DEFAULT 0 COMMENT '删除状态(0:等待删除, 1:删除完成)',
    `retry_count`  int          NOT NULL DEFAULT 0 COMMENT '重试次数',
    `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入队时间',
    `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '状态更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status_update` (`status`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片删除死信队列表';