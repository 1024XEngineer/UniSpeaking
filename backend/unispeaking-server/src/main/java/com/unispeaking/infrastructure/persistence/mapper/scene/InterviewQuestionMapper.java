package com.unispeaking.infrastructure.persistence.mapper.scene;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unispeaking.infrastructure.persistence.entity.scene.InterviewQuestionEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InterviewQuestionMapper
		extends BaseMapper<InterviewQuestionEntity> {
}
