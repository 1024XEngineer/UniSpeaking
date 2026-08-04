package com.unispeaking.infrastructure.persistence.repository.scene;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.po.scene.InterviewQuestionRecord;
import com.unispeaking.domain.vo.scene.InterviewQuestionType;
import com.unispeaking.infrastructure.persistence.entity.scene.InterviewQuestionEntity;
import com.unispeaking.infrastructure.persistence.mapper.scene.InterviewQuestionMapper;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class InterviewQuestionRepository {

	private final InterviewQuestionMapper mapper;

	public InterviewQuestionRepository(InterviewQuestionMapper mapper) {
		this.mapper = mapper;
	}

	public void saveAll(List<InterviewQuestionRecord> records) {
		Objects.requireNonNull(records, "records must not be null");
		if (records.isEmpty()) {
			return;
		}
		try {
			mapper.insert(records.stream().map(this::toEntity).toList());
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public List<InterviewQuestionRecord> findByInterviewId(
			String interviewId) {
		try {
			return mapper.selectList(
						new LambdaQueryWrapper<InterviewQuestionEntity>()
								.eq(
										InterviewQuestionEntity::getInterviewId,
										interviewId)
								.orderByAsc(
										InterviewQuestionEntity::getQuestionNo))
					.stream()
					.map(this::toDomain)
					.toList();
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public Optional<InterviewQuestionRecord> findByKey(
			String interviewId,
			int questionNo) {
		try {
			InterviewQuestionEntity entity = mapper.selectOne(
					key(interviewId, questionNo));
			return entity == null
					? Optional.empty()
					: Optional.of(toDomain(entity));
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public int deleteByKey(String interviewId, int questionNo) {
		try {
			return mapper.delete(key(interviewId, questionNo));
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	public int deleteByInterviewId(String interviewId) {
		try {
			return mapper.delete(
					new LambdaQueryWrapper<InterviewQuestionEntity>()
							.eq(
									InterviewQuestionEntity::getInterviewId,
									interviewId));
		}
		catch (RuntimeException exception) {
			throw persistenceFailure();
		}
	}

	private LambdaQueryWrapper<InterviewQuestionEntity> key(
			String interviewId,
			int questionNo) {
		return new LambdaQueryWrapper<InterviewQuestionEntity>()
				.eq(InterviewQuestionEntity::getInterviewId, interviewId)
				.eq(InterviewQuestionEntity::getQuestionNo, questionNo);
	}

	private InterviewQuestionEntity toEntity(
			InterviewQuestionRecord record) {
		InterviewQuestionEntity entity = new InterviewQuestionEntity();
		entity.setInterviewId(record.interviewId());
		entity.setQuestionNo(record.questionNo());
		entity.setQuestionType(record.questionType().name());
		entity.setQuestionText(record.questionText());
		entity.setCreatedAt(record.createdAt());
		entity.setUpdatedAt(record.updatedAt());
		return entity;
	}

	private InterviewQuestionRecord toDomain(
			InterviewQuestionEntity entity) {
		return new InterviewQuestionRecord(
				entity.getInterviewId(),
				entity.getQuestionNo(),
				InterviewQuestionType.valueOf(entity.getQuestionType()),
				entity.getQuestionText(),
				entity.getCreatedAt(),
				entity.getUpdatedAt());
	}

	private BusinessException persistenceFailure() {
		return new BusinessException(
				"INTERVIEW_QUESTION_PERSISTENCE_FAILED",
				"Interview question persistence operation failed");
	}
}
