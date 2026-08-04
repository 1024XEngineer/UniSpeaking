package com.unispeaking.infrastructure.persistence.mapper.feedback;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unispeaking.infrastructure.persistence.entity.feedback.UserFeedbackEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserFeedbackMapper extends BaseMapper<UserFeedbackEntity> {
}
