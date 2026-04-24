# 给 元信息审核表 增加字段
alter table biz_meta_audit_record
add column `update_time` datetime default now() comment '审核完成时间';

update biz_meta_audit_record set biz_meta_audit_record.update_time  = now();

# 给 图片审核表 添加字段
alter table biz_image_audit_record
add column `update_time` datetime default now() comment '审核完成时间';

update biz_image_audit_record set biz_image_audit_record.update_time  = now();

# 给 笔记审核表 添加字段
alter table biz_note_audit_record
add column `update_time` datetime default now() comment '审核完成时间';

update biz_note_audit_record set biz_note_audit_record.update_time  = now();