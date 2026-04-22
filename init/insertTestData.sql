USE `personal_saas`;

# -- ==========================================
# -- 1. 角色表 (sys_role)
# -- 脚本中已包含初始化，这里补充一个过期/特殊的角色作为测试 (可选)
# -- ==========================================
# INSERT INTO `sys_role` (`role_name`, `role_code`, `daily_api_limit`) VALUES
#     ('访客', 'GUEST', 1);


-- ==========================================
-- 2. 用户表 (sys_user)
-- 密码统一使用 BCrypt 加密的 "123456": $2a$10$8K1p/a0dxE.Y8L.oUIn8Lu7Yl18f5pI1O3vS7l6f9Y.f9Uo7i8I6K
-- ==========================================
INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `email`, `role_id`, `status`) VALUES
                                                                                                    (1, 'creator_admin', '$2a$10$8K1p/a0dxE.Y8L.oUIn8Lu7Yl18f5pI1O3vS7l6f9Y.f9Uo7i8I6K', '系统创建者', 'creator@example.com', 1, 1),
                                                                                                    (2, 'zhangsan', '$2a$10$8K1p/a0dxE.Y8L.oUIn8Lu7Yl18f5pI1O3vS7l6f9Y.f9Uo7i8I6K', '张三', 'zhangsan@test.com', 3, 1),
                                                                                                    (3, 'lisi_vip', '$2a$10$8K1p/a0dxE.Y8L.oUIn8Lu7Yl18f5pI1O3vS7l6f9Y.f9Uo7i8I6K', '李四VIP', 'lisi@test.com', 4, 1),
                                                                                                    (4, 'disabled_user', '$2a$10$8K1p/a0dxE.Y8L.oUIn8Lu7Yl18f5pI1O3vS7l6f9Y.f9Uo7i8I6K', '被禁用用户', 'badboy@test.com', 3, 0),
                                                                                                    (5, 'new_user_wait_active', '$2a$10$8K1p/a0dxE.Y8L.oUIn8Lu7Yl18f5pI1O3vS7l6f9Y.f9Uo7i8I6K', '未激活用户', 'wait_active@test.com', 3, 2);


-- ==========================================
-- 3. 主题/分类表 (biz_topic)
-- 为不同用户创建不同的分类
-- ==========================================
INSERT INTO `biz_topic` (`id`, `user_id`, `topic_name`, `sort_order`) VALUES
                                                                          (1, 1, 'MySQL 深度解析', 1),
                                                                          (2, 1, 'Spring Boot 实战', 2),
                                                                          (3, 1, 'Redis 基础', 3),
                                                                          (4, 1, 'Docker 容器化', 4),
                                                                          (5, 1, 'Vue3 前端开发', 5),
                                                                          (6, 1, 'Linux 运维', 6),
                                                                          (7, 1, '算法与数据结构', 7),
                                                                          (8, 2, '我的日常随笔', 1),
                                                                          (9, 2, 'LeetCode 刷题', 2),
                                                                          (10, 3, '架构设计', 1);


-- ==========================================
-- 4. 标签表 (biz_tag)
-- 为不同用户创建更多标签，用于测试标签接口的增删改查
-- ==========================================
INSERT INTO `biz_tag` (`id`, `user_id`, `tag_name`) VALUES
                                                        (1, 1, '性能优化'),
                                                        (2, 1, '源码阅读'),
                                                        (3, 1, '面试必考'),
                                                        (4, 1, '实战案例'),
                                                        (5, 1, '底层原理'),
                                                        (6, 1, '最佳实践'),
                                                        (7, 1, '避坑指南'),
                                                        (8, 1, '架构模式'),
                                                        (9, 1, '中间件'),
                                                        (10, 1, '工具使用'),
                                                        (11, 2, '踩坑记录'),
                                                        (12, 2, '算法'),
                                                        (13, 2, '学习笔记'),
                                                        (14, 2, '日常总结'),
                                                        (15, 2, '代码片段'),
                                                        (16, 3, '高并发'),
                                                        (17, 3, '分布式'),
                                                        (18, 3, '系统设计'),
                                                        (19, 3, '性能调优'),
                                                        (20, 3, 'DevOps');


-- ==========================================
-- 5. 笔记/文章主表 (biz_note)
-- ==========================================
INSERT INTO `biz_note` (`id`, `user_id`, `topic_id`, `title`, `html_file_path`, `md_file_path`, `is_published`, `storage_type`, `is_missing_photo`) VALUES
                                                                                                                                                        (1, 1, 1, 'MySQL 索引失效的 10 种场景', '/storage/notes/1/1_index_fail.html', '/storage/notes/1/1_index_fail.md', 1, 0, 0),
                                                                                                                                                        (2, 1, 1, 'InnoDB 锁机制详述', '/storage/notes/1/2_innodb_lock.html', '/storage/notes/1/2_innodb_lock.md', 1, 0, 1),
                                                                                                                                                        (3, 2, 8, '2024 年第一篇随笔', '/storage/notes/2/3_essay_01.html', NULL, 0, 0, 0),
                                                                                                                                                        (4, 3, 10, '分布式事务一致性方案', 'https://my-bucket.oss-cn-beijing.aliyuncs.com/notes/4_dist_tx.html', NULL, 1, 1, 0),
                                                                                                                                                        (5, 1, 3, 'Redis 持久化机制详解', '/storage/notes/1/5_redis_persist.html', '/storage/notes/1/5_redis_persist.md', 1, 0, 0),
                                                                                                                                                        (6, 1, 2, 'Spring Boot 3.x.x 新特性', '/storage/notes/1/6_springboot_new.html', '/storage/notes/1/6_springboot_new.md', 1, 0, 0),
                                                                                                                                                        (7, 1, 7, '动态规划解题模板', '/storage/notes/1/7_dp_template.html', '/storage/notes/1/7_dp_template.md', 1, 0, 0),
                                                                                                                                                        (8, 1, 10, '微服务架构设计模式', '/storage/notes/1/8_microservice.html', '/storage/notes/1/8_microservice.md', 1, 0, 0),
                                                                                                                                                        (9, 1, 4, 'Docker 容器网络配置', '/storage/notes/1/9_docker_network.html', '/storage/notes/1/9_docker_network.md', 1, 0, 0),
                                                                                                                                                        (10, 1, 6, 'Linux 性能监控工具', '/storage/notes/1/10_linux_perf.html', '/storage/notes/1/10_linux_perf.md', 1, 0, 0),
                                                                                                                                                        (11, 1, 1, 'MySQL 慢查询优化', '/storage/notes/1/11_slow_query.html', '/storage/notes/1/11_slow_query.md', 1, 0, 0),
                                                                                                                                                        (12, 1, 2, 'Spring Security 实战', '/storage/notes/1/12_spring_security.html', '/storage/notes/1/12_spring_security.md', 1, 0, 0),
                                                                                                                                                        (13, 1, 5, 'Vue3 Composition API', '/storage/notes/1/13_vue3_comp.html', '/storage/notes/1/13_vue3_comp.md', 1, 0, 0);


-- ==========================================
-- 6. 笔记-标签 多对多关联 (biz_note_tag_mapping)
-- 扩展关联关系以使用新增的标签
-- ==========================================
INSERT INTO `biz_note_tag_mapping` (`note_id`, `tag_id`) VALUES
                                                             (1, 1),   -- MySQL 索引失效 → 性能优化
                                                             (1, 2),   -- MySQL 索引失效 → 源码阅读
                                                             (1, 3),   -- MySQL 索引失效 → 面试必考
                                                             (2, 1),   -- InnoDB 锁机制 → 性能优化
                                                             (2, 5),   -- InnoDB 锁机制 → 底层原理
                                                             (2, 3),   -- InnoDB 锁机制 → 面试必考
                                                             (3, 11),  -- 随笔 → 踩坑记录
                                                             (3, 14),  -- 随笔 → 日常总结
                                                             (4, 16),  -- 分布式事务 → 高并发
                                                             (4, 17),  -- 分布式事务 → 分布式
                                                             (4, 18),  -- 分布式事务 → 系统设计
                                                             (5, 1),   -- Redis 持久化 → 性能优化
                                                             (5, 2),   -- Redis 持久化 → 源码阅读
                                                             (5, 9),   -- Redis 持久化 → 中间件
                                                             (6, 3),   -- Spring Boot 新特性 → 面试必考
                                                             (6, 7),   -- Spring Boot 新特性 → 避坑指南
                                                             (7, 4),   -- 动态规划 → 算法
                                                             (7, 3),   -- 动态规划 → 面试必考
                                                             (8, 8),   -- 微服务架构 → 架构模式
                                                             (8, 17),  -- 微服务架构 → 分布式
                                                             (8, 18),  -- 微服务架构 → 系统设计
                                                             (9, 6),   -- Docker 网络 → 最佳实践
                                                             (9, 10),  -- Docker 网络 → 工具使用
                                                             (10, 1),  -- Linux 性能监控 → 性能优化
                                                             (10, 10), -- Linux 性能监控 → 工具使用
                                                             (11, 1),  -- MySQL 慢查询 → 性能优化
                                                             (11, 7),  -- MySQL 慢查询 → 避坑指南
                                                             (12, 3),  -- Spring Security → 面试必考
                                                             (12, 4),  -- Spring Security → 实战案例
                                                             (13, 6),  -- Vue3 Composition API → 最佳实践
                                                             (13, 10); -- Vue3 Composition API → 工具使用


-- ==========================================
-- 7. 图片资源映射表 (biz_image)
-- 说明：biz_image 仅支持云存储(1=阿里云OSS, 2=Cloudflare R2预留)
-- ==========================================
INSERT INTO `biz_image` (`id`, `user_id`, `topic_id`, `filename`, `oss_url`, `storage_type`, `file_size`, `is_public`, `is_pass`) VALUES
                                                                                                              (1, 1, 1, 'mysql_b_tree.png', 'https://oss.example.com/u1/mysql_b_tree.png', 1, 102455, 1, 1),
                                                                                                              (2, 1, 1, 'explain_result.jpg', 'https://oss.example.com/u1/explain_result.jpg', 1, 56789, 0, 1),
                                                                                                              (3, 3, 10, 'raft_protocol.png', 'https://oss.example.com/u3/raft_protocol.png', 1, 204800, 1, 1),
                                                                                                              (4, 2, 8, 'daily_note_cover.png', 'https://oss.example.com/u2/daily_note_cover.png', 1, 40960, 0, 0),
                                                                                                              (5, 3, 10, 'eventual_consistency.png', 'https://r2.example.com/u3/eventual_consistency.png', 2, 99888, 1, 0);


-- ==========================================
-- 8. 每日 API 调用统计表 (biz_api_daily_usage)
-- 模拟用户今日已使用的额度
-- ==========================================
INSERT INTO `biz_api_daily_usage` (`user_id`, `record_date`, `used_count`) VALUES
                                                                               (1, CURDATE(), 50),     -- 管理员今天用了 50 次
                                                                               (2, CURDATE(), 5),      -- 普通用户张三今天已经用完了（上限5次）
                                                                               (3, CURDATE(), 10),     -- VIP 李四今天用了 10 次
                                                                               (2, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 3),
                                                                               (3, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 35);


-- ==========================================
-- 9. API 异步任务明细表 (biz_api_task_log)
-- ==========================================
INSERT INTO `biz_api_task_log` (`user_id`, `task_type`, `task_status`, `result_url`, `error_msg`, `finish_time`) VALUES
                                                                                                                     (1, 'analyze_text', 1, 'https://oss.example.com/res/report_001.pdf', NULL, NOW()),
                                                                                                                     (2, 'generate_audio', 2, NULL, 'Python Script Timeout: Connection refused', NOW()),
                                                                                                                     (3, 'analyze_text', 0, NULL, NULL, NULL); -- 处理中任务


-- ==========================================
-- 10. 笔记-图片引用映射表 (biz_note_image_mapping)
-- ==========================================
INSERT INTO `biz_note_image_mapping`
(`note_id`, `image_id`, `note_user_id`, `image_user_id`, `parsed_image_name`, `note_title`, `is_cross_user`, `is_deleted`) VALUES
    (1, 1, 1, 1, 'mysql_b_tree.png', 'MySQL 索引失效的 10 种场景', 0, 0),
    (1, 2, 1, 1, 'explain_result.jpg', 'MySQL 索引失效的 10 种场景', 0, 0),
    (4, 3, 3, 3, 'raft_protocol.png', '分布式事务一致性方案', 0, 0),
    (8, 3, 1, 3, 'raft_protocol.png', '微服务架构设计模式', 1, 0),
    (3, 4, 2, 2, 'daily_note_cover.png', '2024 年第一篇随笔', 0, 1);


-- ==========================================
-- 11. 元数据审核表 (biz_meta_audit_record)
-- ==========================================
INSERT INTO `biz_meta_audit_record`
(`applicant_user_id`, `apply_type`, `target_id`, `apply_reason`, `status`, `reviewer_user_id`, `reject_reason`, `review_time`) VALUES
    (2, 1, 8, '申请将主题公开给团队共享', 1, 1, NULL, NOW()),
    (2, 2, 11, '该标签用于项目复盘', 2, 1, '标签描述过于模糊，请补充语义', NOW()),
    (3, 1, 10, '申请加急审核架构主题', 0, NULL, NULL, NULL);


-- ==========================================
-- 12. 图片审核表 (biz_image_audit_record)
-- ==========================================
INSERT INTO `biz_image_audit_record`
(`applicant_user_id`, `image_id`, `apply_reason`, `status`, `reviewer_user_id`, `reject_reason`, `review_time`) VALUES
    (2, 4, '封面图用于公开笔记，申请通过审核', 0, NULL, NULL, NULL),
    (3, 5, '该图为原创架构图，申请公开引用', 2, 1, '当前图片清晰度不足，请重新上传高清版本', NOW()),
    (1, 1, '用于公共知识库示例图', 1, 1, NULL, NOW());


-- ==========================================
-- 13. 笔记审核表 (biz_note_audit_record)
-- ==========================================
INSERT INTO `biz_note_audit_record`
(`applicant_user_id`, `note_id`, `apply_reason`, `status`, `reviewer_user_id`, `reject_reason`, `review_time`) VALUES
    (2, 3, '内容已补充完整，申请发布', 0, NULL, NULL, NULL),
    (1, 2, '技术笔记申请公开', 1, 1, NULL, NOW()),
    (3, 4, '申请首页推荐', 2, 1, '内容缺少可复现步骤，请补充后重提', NOW());


-- ==========================================
-- 14. 图片删除死信队列表 (biz_image_delete_dead_letter)
-- ==========================================
INSERT INTO `biz_image_delete_dead_letter` (`image_url`, `status`, `retry_count`) VALUES
    ('https://oss.example.com/u2/expired_audit_001.png', 0, 0),
    ('https://oss.example.com/u1/temp/deprecated_cover.png', 0, 2),
    ('https://r2.example.com/u3/legacy/old_architecture.png', 1, 1);