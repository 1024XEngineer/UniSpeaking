ALTER TABLE user_preference
ADD COLUMN weekly_duration_target_minutes INTEGER,
ADD COLUMN weekly_training_count_target INTEGER;

COMMENT ON COLUMN user_preference.weekly_duration_target_minutes IS
'用户每周口语时长目标，单位为分钟；空值由业务层使用默认值';

COMMENT ON COLUMN user_preference.weekly_training_count_target IS
'用户每周有效训练次数目标；空值由业务层使用默认值';
