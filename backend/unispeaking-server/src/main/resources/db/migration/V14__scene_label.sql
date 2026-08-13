alter table scene
    add column label varchar(16);

update scene
set label = '其他'
where label is null;

alter table scene
    alter column label set not null;

alter table scene
    add constraint chk_scene_label
        check (label in ('餐饮', '购物', '出行', '住宿', '健康', '职场', '社交', '学习', '服务', '其他'));

comment on column scene.label is '自定义场景标签，由生成模型从固定十类中选择';
