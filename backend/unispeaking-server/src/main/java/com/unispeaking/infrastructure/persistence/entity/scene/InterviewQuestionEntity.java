package com.unispeaking.infrastructure.persistence.entity.scene;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The database primary key is (interview_id, question_no). Repository
 * operations must always address both columns because MyBatis-Plus does not
 * support composite {@code @TableId} mappings.
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("interview_question")
public class InterviewQuestionEntity {

	private String interviewId;
	private Integer questionNo;
	private String questionType;
	private String questionText;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;
}
