package com.unispeaking.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unispeaking.common.evaluation.model.EndingTone;
import com.unispeaking.common.evaluation.model.PronunciationAssessmentResult;
import com.unispeaking.common.evaluation.model.PronunciationPhonemeResult;
import com.unispeaking.common.evaluation.model.PronunciationWordResult;
import com.unispeaking.common.evaluation.model.WordReadStatus;
import com.unispeaking.domain.dto.evaluation.DialogueReportResult;
import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.po.auth.UserAccount;
import com.unispeaking.domain.po.auth.UserRole;
import com.unispeaking.domain.po.auth.UserStatus;
import com.unispeaking.domain.po.feedback.UserFeedback;
import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.domain.po.profile.WeeklyLearningGoals;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.domain.po.scene.InterviewQuestionRecord;
import com.unispeaking.domain.po.scene.InterviewRecord;
import com.unispeaking.domain.po.scene.InterviewReportRecord;
import com.unispeaking.domain.vo.scene.InterviewDifficulty;
import com.unispeaking.domain.vo.scene.InterviewQuestionType;
import com.unispeaking.domain.vo.scene.InterviewReportDimension;
import com.unispeaking.domain.vo.scene.InterviewReportType;
import com.unispeaking.domain.vo.scene.TargetRoleSummary;
import com.unispeaking.domain.vo.feedback.FeedbackStatus;
import com.unispeaking.infrastructure.persistence.entity.evaluation.CustomTurnEvaluation;
import com.unispeaking.infrastructure.persistence.entity.evaluation.PronunciationWordDetail;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SceneSentenceReadingRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SessionEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.TurnEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.feedback.FeedbackRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.MybatisSceneRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.InterviewQuestionRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.InterviewReportRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.InterviewRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.infrastructure.persistence.repository.user.MybatisUserAccountRepository;
import com.unispeaking.infrastructure.persistence.repository.user.MybatisUserProfileRepository;
import com.unispeaking.infrastructure.persistence.repository.user.WeeklyLearningGoalRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class PostgresPersistenceIT {

	@Container
	static final PostgreSQLContainer<?> POSTGRES =
			new PostgreSQLContainer<>("postgres:17-alpine")
					.withDatabaseName("unispeaking_it")
					.withUsername("unispeaking")
					.withPassword("unispeaking");

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
		registry.add("spring.flyway.enabled", () -> true);
		registry.add("spring.flyway.baseline-on-migrate", () -> true);
		registry.add("spring.flyway.baseline-version", () -> "0");
		registry.add("spring.flyway.locations", () -> "classpath:db/migration");
		registry.add("spring.sql.init.mode", () -> "never");
		registry.add(
				"mybatis-plus.type-handlers-package",
				() -> "com.unispeaking.infrastructure.persistence.typehandler");
	}

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	private MybatisUserAccountRepository userAccountRepository;

	@Autowired
	private MybatisUserProfileRepository userProfileRepository;

	@Autowired
	private WeeklyLearningGoalRepository weeklyLearningGoalRepository;

	@Autowired
	private MybatisSceneRepository sceneRepository;

	@Autowired
	private InterviewRepository interviewRepository;

	@Autowired
	private InterviewQuestionRepository interviewQuestionRepository;

	@Autowired
	private InterviewReportRepository interviewReportRepository;

	@Autowired
	private SessionMessageRepository sessionMessageRepository;

	@Autowired
	private TurnEvaluationRepository turnEvaluationRepository;

	@Autowired
	private SessionEvaluationRepository sessionEvaluationRepository;

	@Autowired
	private SceneSentenceReadingRepository sentenceReadingRepository;

	@Autowired
	private FeedbackRepository feedbackRepository;

	@BeforeEach
	void clearBusinessTables() {
		jdbcTemplate.execute("""
				TRUNCATE TABLE
				    user_feedback,
				    ielts_evaluation,
				    ielts,
				    user_ielts,
				    practice_session,
				    sentence_evaluation,
				    session_evaluation,
				    turn_evaluation,
				    session_message,
				    interview_report,
				    interview_question,
				    interview,
				    sentence,
				    phrase,
				    "word",
				    scene,
				    user_preference,
				    "user"
				""");
	}

	@Test
	void migratesEmptyDatabaseAndRegistersFlywayHistory() {
		List<String> migrationVersions = jdbcTemplate.queryForList(
				"""
				SELECT version
				FROM flyway_schema_history
				WHERE success
				ORDER BY installed_rank
				""",
				String.class);
		Integer topicCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM ielts_topic",
				Integer.class);
		Integer questionCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM ielts_question",
				Integer.class);
		Integer questionLikeTitleCount = jdbcTemplate.queryForObject(
				"""
				SELECT COUNT(*)
				FROM ielts_topic
				WHERE title ~* '^(describe|what|why|how|do |did |are |is |have |would |talk about|tell me)'
				""",
				Integer.class);
		Integer helpTableCount = jdbcTemplate.queryForObject(
				"""
				SELECT COUNT(*)
				FROM information_schema.tables
				WHERE table_schema = 'public'
				  AND table_name IN (
				      'user_achievement_unlock',
				      'user_achievement_state',
				      'user_feedback'
				  )
				""",
				Integer.class);
		String successFactorType = jdbcTemplate.queryForObject(
				"""
				SELECT data_type
				FROM information_schema.columns
				WHERE table_schema = 'public'
				  AND table_name = 'scene'
				  AND column_name = 'success_factor'
				""",
				String.class);

		assertEquals(List.of("1", "2", "3", "4", "5", "6", "7"), migrationVersions);
		assertEquals(303, topicCount);
		assertEquals(1771, questionCount);
		assertEquals(0, questionLikeTitleCount);
		assertEquals(3, helpTableCount);
		assertEquals("jsonb", successFactorType);
	}

	@Test
	void createsInterviewTablesWithExactColumnsAndRelationships() {
		assertEquals(
				List.of(
						"id|character varying|64|||NO",
						"user_id|uuid||||NO",
						"session_id|character varying|64|||NO",
						"job_title|character varying|255|||NO",
						"difficulty|character varying|16|||NO",
						"role_summary|jsonb||||NO",
						"recording_object_key|character varying|512|||YES",
						"recording_duration_seconds|integer||32|0|YES",
						"completed_at|timestamp with time zone||||YES",
						"created_at|timestamp with time zone||||NO",
						"updated_at|timestamp with time zone||||NO"),
				columnDefinitions("interview"));
		assertEquals(
				List.of(
						"interview_id|character varying|64|||NO",
						"question_no|integer||32|0|NO",
						"question_type|character varying|16|||NO",
						"question_text|text||||NO",
						"created_at|timestamp with time zone||||NO",
						"updated_at|timestamp with time zone||||NO"),
				columnDefinitions("interview_question"));
		assertEquals(
				List.of(
						"interview_id|character varying|64|||NO",
						"report_type|character varying|16|||NO",
						"overall_score|numeric||4|1|NO",
						"overall_summary|text||||NO",
						"fluency_score|numeric||4|1|NO",
						"fluency_evaluation|text||||NO",
						"fluency_action_suggestion|text||||NO",
						"logic_coherence_score|numeric||4|1|NO",
						"logic_coherence_evaluation|text||||NO",
						"logic_coherence_action_suggestion|text||||NO",
						"grammar_control_score|numeric||4|1|NO",
						"grammar_control_evaluation|text||||NO",
						"grammar_control_action_suggestion|text||||NO",
						"pronunciation_intelligibility_score|numeric||4|1|NO",
						"pronunciation_intelligibility_evaluation|text||||NO",
						"pronunciation_intelligibility_action_suggestion|text||||NO",
						"vocabulary_expression_score|numeric||4|1|NO",
						"vocabulary_expression_evaluation|text||||NO",
						"vocabulary_expression_action_suggestion|text||||NO",
						"created_at|timestamp with time zone||||NO",
						"updated_at|timestamp with time zone||||NO"),
				columnDefinitions("interview_report"));

		assertEquals(
				Map.of(
						"interview", "id",
						"interview_question", "interview_id,question_no",
						"interview_report", "interview_id"),
				jdbcTemplate.query(
						"""
						SELECT tc.table_name,
						       STRING_AGG(kcu.column_name, ',' ORDER BY kcu.ordinal_position)
						FROM information_schema.table_constraints tc
						JOIN information_schema.key_column_usage kcu
						  ON kcu.constraint_schema = tc.constraint_schema
						 AND kcu.constraint_name = tc.constraint_name
						WHERE tc.table_schema = 'public'
						  AND tc.table_name IN ('interview', 'interview_question', 'interview_report')
						  AND tc.constraint_type = 'PRIMARY KEY'
						GROUP BY tc.table_name
						""",
						resultSet -> {
							Map<String, String> keys = new java.util.HashMap<>();
							while (resultSet.next()) {
								keys.put(resultSet.getString(1), resultSet.getString(2));
							}
							return keys;
						}));
		assertEquals(
				List.of("session_id"),
				jdbcTemplate.queryForList(
						"""
						SELECT kcu.column_name
						FROM information_schema.table_constraints tc
						JOIN information_schema.key_column_usage kcu
						  ON kcu.constraint_schema = tc.constraint_schema
						 AND kcu.constraint_name = tc.constraint_name
						WHERE tc.table_schema = 'public'
						  AND tc.table_name = 'interview'
						  AND tc.constraint_type = 'UNIQUE'
						""",
						String.class));
		String completedAssetIndex = jdbcTemplate.queryForObject(
				"""
				SELECT indexdef
				FROM pg_indexes
				WHERE schemaname = 'public'
				  AND tablename = 'interview'
				  AND indexname = 'idx_interview_user_completed_at'
				""",
				String.class);
		assertTrue(completedAssetIndex.contains("(user_id, completed_at DESC)"));
		assertTrue(completedAssetIndex.contains("WHERE (completed_at IS NOT NULL)"));
		assertEquals(
				0,
				jdbcTemplate.queryForObject(
						"""
						SELECT COUNT(*)
						FROM information_schema.table_constraints
						WHERE table_schema = 'public'
						  AND table_name IN ('interview', 'interview_question', 'interview_report')
						  AND constraint_type = 'FOREIGN KEY'
						""",
						Integer.class));
	}

	@Test
	void enforcesInterviewSchemaChecksAndSessionUniqueness() {
		insertInterview("interview_valid", "session_valid", "STANDARD", 0);

		assertThrows(
				DataIntegrityViolationException.class,
				() -> insertInterview(
						"interview_bad_difficulty",
						"session_bad_difficulty",
						"EXPERT",
						0));
		assertThrows(
				DataIntegrityViolationException.class,
				() -> insertInterview(
						"interview_bad_duration",
						"session_bad_duration",
						"BASIC",
						-1));
		assertThrows(
				DataIntegrityViolationException.class,
				() -> insertInterview(
						"interview_duplicate_session",
						"session_valid",
						"CHALLENGE",
						1));

		jdbcTemplate.update(
				"""
				INSERT INTO interview_question
				    (interview_id, question_no, question_type, question_text)
				VALUES ('interview_valid', 1, 'MAIN', 'Tell me about yourself.')
				""");
		assertThrows(
				DataIntegrityViolationException.class,
				() -> jdbcTemplate.update("""
						INSERT INTO interview_question
						    (interview_id, question_no, question_type, question_text)
						VALUES ('interview_bad_no', 0, 'MAIN', 'Invalid number')
						"""));
		assertThrows(
				DataIntegrityViolationException.class,
				() -> jdbcTemplate.update("""
						INSERT INTO interview_question
						    (interview_id, question_no, question_type, question_text)
						VALUES ('interview_bad_type', 1, 'OPTIONAL', 'Invalid type')
						"""));

		insertReport("report_valid", "FULL");
		assertThrows(
				DataIntegrityViolationException.class,
				() -> insertReport("report_bad_type", "DRAFT"));
		List<String> scoreColumns = List.of(
				"overall_score",
				"fluency_score",
				"logic_coherence_score",
				"grammar_control_score",
				"pronunciation_intelligibility_score",
				"vocabulary_expression_score");
		for (int index = 0; index < scoreColumns.size(); index++) {
			String interviewId = "report_bad_score_" + index;
			String scoreColumn = scoreColumns.get(index);
			insertReport(interviewId, "PARTIAL");
			assertThrows(
					DataIntegrityViolationException.class,
					() -> jdbcTemplate.update(
							"UPDATE interview_report SET " + scoreColumn
									+ " = 100.1 WHERE interview_id = ?",
							interviewId));
		}
		assertThrows(
				DataIntegrityViolationException.class,
				() -> jdbcTemplate.update("""
						UPDATE interview_report
						SET overall_score = -0.1
						WHERE interview_id = 'report_valid'
						"""));
	}

	@Test
	void persistsAndPhysicallyDeletesCompleteInterviewAssets() {
		UUID ownerId = UUID.fromString(
				"11111111-1111-4111-8111-111111111111");
		UUID otherUserId = UUID.fromString(
				"22222222-2222-4222-8222-222222222222");
		OffsetDateTime createdAt = OffsetDateTime.of(
				2026, 8, 4, 8, 0, 0, 0, ZoneOffset.UTC);
		TargetRoleSummary roleSummary = new TargetRoleSummary(
				"负责企业级 SaaS 产品规划与交付。",
				List.of("分析用户需求", "协调跨团队交付"),
				List.of("产品规划", "数据分析"),
				List.of("英语业务沟通"));
		InterviewRecord interview = new InterviewRecord(
				"interview_repository_it",
				ownerId,
				"interview_session_it",
				"Product Manager",
				InterviewDifficulty.CHALLENGE,
				roleSummary,
				null,
				null,
				null,
				createdAt,
				createdAt);

		interviewRepository.create(interview);

		InterviewRecord pending = interviewRepository
				.findByIdAndUserId(interview.id(), ownerId)
				.orElseThrow();
		assertEquals(roleSummary, pending.roleSummary());
		assertEquals(List.of(
				"overview",
				"qualification_requirements",
				"required_skills",
				"responsibilities"),
				jdbcTemplate.queryForList(
						"SELECT jsonb_object_keys(role_summary) "
								+ "FROM interview WHERE id = ? ORDER BY 1",
						String.class,
						interview.id()));
		assertTrue(interviewRepository
				.findByIdAndUserId(interview.id(), otherUserId)
				.isEmpty());
		assertNull(pending.recordingObjectKey());
		assertNull(pending.recordingDurationSeconds());
		assertNull(pending.completedAt());

		interviewQuestionRepository.saveAll(List.of(
				question(interview.id(), 3, InterviewQuestionType.MAIN, createdAt),
				question(interview.id(), 1, InterviewQuestionType.MAIN, createdAt),
				question(
						interview.id(),
						2,
						InterviewQuestionType.FOLLOW_UP,
						createdAt)));

		assertEquals(
				List.of(1, 2, 3),
				interviewQuestionRepository.findByInterviewId(interview.id())
						.stream()
						.map(InterviewQuestionRecord::questionNo)
						.toList());
		assertEquals(
				InterviewQuestionType.FOLLOW_UP,
				interviewQuestionRepository.findByKey(interview.id(), 2)
						.orElseThrow()
						.questionType());
		assertEquals(1, interviewQuestionRepository.deleteByKey(
				interview.id(),
				2));
		assertTrue(interviewQuestionRepository
				.findByKey(interview.id(), 2)
				.isEmpty());
		assertEquals(
				List.of(1, 3),
				interviewQuestionRepository.findByInterviewId(interview.id())
						.stream()
						.map(InterviewQuestionRecord::questionNo)
						.toList());

		InterviewReportRecord report = interviewReport(
				interview.id(),
				createdAt.plusMinutes(5));
		interviewReportRepository.save(report);
		assertEquals(
				report,
				interviewReportRepository.findByInterviewId(interview.id())
						.orElseThrow());

		OffsetDateTime completedAt = createdAt.plusMinutes(6);
		interviewRepository.completeAssetMetadata(
				interview.id(),
				"interviews/recordings/repository-it.mp3",
				366,
				completedAt);
		InterviewRecord completed = interviewRepository.findById(interview.id())
				.orElseThrow();
		assertEquals("interviews/recordings/repository-it.mp3",
				completed.recordingObjectKey());
		assertEquals(366, completed.recordingDurationSeconds());
		assertEquals(completedAt, completed.completedAt());
		assertEquals(completedAt, completed.updatedAt());

		assertEquals(1,
				interviewReportRepository.deleteByInterviewId(interview.id()));
		assertTrue(interviewReportRepository
				.findByInterviewId(interview.id())
				.isEmpty());
		assertEquals(2,
				interviewQuestionRepository.deleteByInterviewId(interview.id()));
		assertTrue(interviewQuestionRepository
				.findByInterviewId(interview.id())
				.isEmpty());
		assertEquals(1, interviewRepository.deleteById(interview.id()));
		assertTrue(interviewRepository.findById(interview.id()).isEmpty());
	}

	@Test
	void persistsIeltsUserContentAndBandEvaluation() {
		UUID userId = UUID.fromString("33333333-3333-4333-8333-333333333333");
		jdbcTemplate.update(
				"""
				INSERT INTO user_ielts
				    (user_id, target_score, today_completed_count, preferred_voice)
				VALUES (?::uuid, 7.5, 4, 'Clara')
				""",
				userId.toString());
		jdbcTemplate.update(
				"""
				INSERT INTO practice_session
				    (session_id, user_id, scene_type, status, started_at)
				VALUES ('session_ielts_it1', ?::uuid, 'IELTS_SCENE', 'ACTIVE',
				        CURRENT_TIMESTAMP)
				""",
				userId.toString());
		jdbcTemplate.update(
				"""
				INSERT INTO ielts
				    (ielts_id, user_id, mode, selected_part, selected_topic_id,
				     content)
				VALUES ('session_ielts_it1', ?::uuid, 'PART_PRACTICE', 'PART_1',
				        'ielts_group_it1',
				        '{
				          "part1": [{
				            "question": "What do you enjoy doing on weekends?",
				            "recommended_expressions": ["I usually...", "I tend to..."]
				          }],
				          "part2": [],
				          "part3": []
				        }'::jsonb)
				""",
				userId.toString());
		jdbcTemplate.update("""
				INSERT INTO ielts_evaluation
				    (session_id, ielts_id, part, assessment_type,
				     overall_band_score, fluency_coherence_score,
				     lexical_resource_score,
				     grammatical_range_accuracy_score,
				     pronunciation_score, summary, strengths, improvements)
				VALUES ('session_ielts_it1', 'session_ielts_it1', 'PART_1',
				        'DIAGNOSTIC', 7.0, 7.5, 7.0, 6.5, 7.0,
				        '表达清晰，细节可以更充分。', ARRAY['词汇自然'],
				        ARRAY['补充例子'])
				""");
		jdbcTemplate.update(
				"""
				UPDATE user_ielts
				SET today_completed_count = today_completed_count + 1
				WHERE user_id = ?::uuid
				  AND today_completed_count < 5
				""",
				userId.toString());

		assertEquals(
				new BigDecimal("7.0"),
				jdbcTemplate.queryForObject(
						"""
						SELECT overall_band_score
						FROM ielts_evaluation
						WHERE session_id = 'session_ielts_it1'
						""",
						BigDecimal.class));
		assertEquals(
				"session_ielts_it1",
				jdbcTemplate.queryForObject(
						"""
						SELECT ielts_id
						FROM ielts_evaluation
						WHERE session_id = 'session_ielts_it1'
						""",
						String.class));
		assertEquals(
				"array",
				jdbcTemplate.queryForObject(
						"""
						SELECT jsonb_typeof(
						    content -> 'part1' -> 0 -> 'recommended_expressions')
						FROM ielts
						WHERE ielts_id = 'session_ielts_it1'
						""",
						String.class));
		assertEquals(
				5,
				jdbcTemplate.queryForObject(
						"""
						SELECT today_completed_count
						FROM user_ielts
						WHERE user_id = ?::uuid
						""",
						Integer.class,
						userId.toString()));
	}

	@Test
	void persistsUserProfileAndLastLoginAgainstPostgres() {
		UUID userId = UUID.fromString("11111111-1111-4111-8111-111111111111");
		Instant createdAt = Instant.parse("2026-07-31T06:00:00Z");
		UserAccount account = new UserAccount(
				userId,
				"ci@example.com",
				"encoded-password",
				"CI User",
				UserRole.USER,
				UserStatus.ACTIVE,
				0,
				null,
				createdAt,
				createdAt);

		userAccountRepository.create(account);
		userAccountRepository.updateLastLoginAt(
				userId,
				Instant.parse("2026-07-31T07:00:00Z"));
		UserProfile profile = new UserProfile(
				userId.toString(),
				"B",
				"Clara",
				"MODERATE",
				"zh-CN",
				"喜欢旅行",
				"{\"translation_enabled\":true}");
		userProfileRepository.save(profile);
		userProfileRepository.save(profile.withPreferences(
				"James",
				"NATURAL",
				"C",
				"喜欢旅行和咖啡"));
		assertNull(jdbcTemplate.queryForObject(
				"SELECT weekly_duration_target_minutes FROM user_preference WHERE user_id = ?",
				Integer.class,
				userId));
		assertEquals(
				WeeklyLearningGoals.defaults(),
				weeklyLearningGoalRepository.findByUserId(userId).orElseThrow());
		WeeklyLearningGoals goals = new WeeklyLearningGoals(180, 6);
		weeklyLearningGoalRepository.save(userId, goals);

		assertEquals(
				"ci@example.com",
				userAccountRepository.findById(userId).orElseThrow().username());
		assertEquals(
				Instant.parse("2026-07-31T07:00:00Z"),
				userAccountRepository.findByUsername("ci@example.com")
						.orElseThrow()
						.lastLoginAt());
		UserProfile saved = userProfileRepository
				.findByUserId(userId.toString())
				.orElseThrow();
		assertEquals("James", saved.voiceId());
		assertEquals("C", saved.level());
		assertEquals("喜欢旅行和咖啡", saved.memoryText());
		assertEquals(
				goals,
				weeklyLearningGoalRepository.findByUserId(userId).orElseThrow());
	}

	@Test
	void persistsAnonymousFeedbackAndTracksResolution() {
		Instant createdAt = Instant.parse("2026-08-04T08:00:00Z");
		UserFeedback submitted = new UserFeedback(
				UUID.fromString("22222222-2222-4222-8222-222222222222"),
				"FB-20260804-ABCDEF123456",
				null,
				"a".repeat(64),
				"audio",
				"麦克风无法使用",
				"允许权限后仍没有声音",
				"Chrome 138",
				FeedbackStatus.SUBMITTED,
				null,
				null,
				createdAt,
				createdAt);

		feedbackRepository.create(submitted);
		UserFeedback stored = feedbackRepository
				.findByFeedbackNo(submitted.feedbackNo())
				.orElseThrow();
		UserFeedback resolved = stored.withResolution(
				FeedbackStatus.RESOLVED,
				"请重新选择系统输入设备后再试",
				createdAt.plusSeconds(60));
		feedbackRepository.update(stored, resolved);

		UserFeedback result = feedbackRepository
				.findByFeedbackNo(submitted.feedbackNo())
				.orElseThrow();
		assertEquals(FeedbackStatus.RESOLVED, result.status());
		assertEquals("请重新选择系统输入设备后再试", result.reply());
		assertEquals(createdAt.plusSeconds(60), result.repliedAt());
	}

	@Test
	void persistsSceneContentReadsAssetsAndHonorsSoftDelete() throws Exception {
		CustomSceneDefinition definition = sceneDefinition();
		SceneGenerationResponse response = new SceneGenerationResponse(
				definition.sceneId(),
				definition.wordList(),
				definition.phraseList(),
				definition.sentenceList(),
				"prompt");

		sceneRepository.saveCustomScene(definition, response);

		SceneGenerationResponse generated = sceneRepository
				.findGeneratedById(definition.sceneId())
				.orElseThrow();
		assertEquals("word_it1", generated.wordList().getFirst().contentId());
		assertEquals("phrase_it1", generated.phraseList().getFirst().contentId());
		assertEquals("sentence_it1", generated.sentenceList().getFirst().contentId());
		assertEquals(
				objectMapper.readTree("{\"minimum_user_turns\":2}"),
				objectMapper.readTree(sceneRepository
						.findCustomDefinitionById(definition.sceneId())
						.orElseThrow()
						.successFactorJson()));
		assertEquals(1, sceneRepository.findAssetsByUserId(definition.userId()).size());
		assertTrue(sceneRepository.findAssetsByUserId("not-a-uuid").isEmpty());

		jdbcTemplate.update(
				"UPDATE scene SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
				definition.sceneId());

		assertTrue(sceneRepository.findGeneratedById(definition.sceneId()).isEmpty());
		assertTrue(sceneRepository.findAssetsByUserId(definition.userId()).isEmpty());
	}

	@Test
	void storesMessagesAndEvaluationJsonbWithCompositeKeys() {
		sessionMessageRepository.append(
				"custom_it1",
				"session_it1",
				2,
				new Message(0, "  Welcome.  ", null));
		sessionMessageRepository.append(
				"custom_it1",
				"session_it1",
				1,
				new Message(1, "  Hello.  ", null));
		sessionMessageRepository.append(
				"custom_it1",
				"session_old",
				1,
				new Message(1, "Old message", null));

		assertEquals(
				List.of("Hello.", "Welcome."),
				sessionMessageRepository.findMessages("session_it1").stream()
						.map(Message::content)
						.toList());
		assertEquals(
				"custom_it1",
				sessionMessageRepository.findSceneId("session_it1").orElseThrow());
		assertEquals(
				1,
				sessionMessageRepository.deleteObsoleteForScene(
						"custom_it1",
						"session_it1"));

		CustomTurnEvaluation first = turnEvaluation(1, new BigDecimal("82"));
		CustomTurnEvaluation second = turnEvaluation(2, new BigDecimal("86"));
		turnEvaluationRepository.upsert(first);
		turnEvaluationRepository.upsert(second);
		turnEvaluationRepository.upsert(turnEvaluation(
				1,
				new BigDecimal("91")));

		List<CustomTurnEvaluation> saved =
				turnEvaluationRepository.findAll("session_it1");
		assertEquals(2, saved.size());
		assertEquals(new BigDecimal("91.00"), saved.getFirst().overallScore());
		assertEquals("coffee", saved.getFirst().words().getFirst().text());
		assertEquals(
				1,
				turnEvaluationRepository.findBefore("session_it1", 2).size());
		assertEquals(
				"object",
				jdbcTemplate.queryForObject(
						"""
						SELECT jsonb_typeof(pronunciation_details)
						FROM turn_evaluation
						WHERE session_id = 'session_it1' AND turn_no = 1
						""",
						String.class));
	}

	@Test
	void storesSessionArraysAndSentenceReadingDetails() {
		CustomSceneDefinition definition = sceneDefinition();
		sceneRepository.saveCustomScene(
				definition,
				new SceneGenerationResponse(
						definition.sceneId(),
						definition.wordList(),
						definition.phraseList(),
						definition.sentenceList(),
						"prompt"));
		DialogueReportResult report = new DialogueReportResult(
				new BigDecimal("88"),
				new BigDecimal("87"),
				new BigDecimal("86"),
				new BigDecimal("85"),
				new BigDecimal("84"),
				new BigDecimal("86"),
				"表达稳定",
				List.of("内容清楚", "语速自然"),
				List.of("增加连接词"));

		sessionEvaluationRepository.save(
				definition.sceneId(),
				"session_it1",
				report);
		sessionEvaluationRepository.save(
				definition.sceneId(),
				"session_it1",
				report);
		String readingId = sentenceReadingRepository.saveAttempt(
				definition.sceneId(),
				definition.sentenceList().getFirst(),
				pronunciationAssessment());

		assertEquals(
				List.of("内容清楚", "语速自然"),
				sessionEvaluationRepository.find("session_it1")
						.orElseThrow()
						.strengths());
		assertEquals(
				definition.sceneId(),
				sessionEvaluationRepository.findRecord("session_it1")
						.orElseThrow()
						.sceneId());
		assertEquals(
				1,
				sessionEvaluationRepository.findBySceneId(definition.sceneId()).size());
		assertEquals(
				definition.sceneId(),
				sentenceReadingRepository
						.findSceneIdBySentenceId("sentence_it1")
						.orElseThrow());
		assertTrue(readingId.startsWith("sentence_reading_"));
		assertEquals(
				"object",
				jdbcTemplate.queryForObject(
						"SELECT jsonb_typeof(score_detail) FROM sentence_evaluation WHERE id = ?",
						String.class,
						readingId));
	}

	@Test
	void baselinesLegacySchemaAtZeroAndKeepsExistingData() {
		String schema = "legacy_ci";
		jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
		jdbcTemplate.execute("CREATE SCHEMA " + schema);
		jdbcTemplate.execute("""
				CREATE TABLE legacy_ci."user" (
				    id UUID PRIMARY KEY,
				    username VARCHAR(128) NOT NULL UNIQUE,
				    password_hash VARCHAR(255) NOT NULL,
				    nickname VARCHAR(32),
				    role VARCHAR(16) NOT NULL DEFAULT 'USER',
				    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
				    auth_version BIGINT NOT NULL DEFAULT 0,
				    last_login_at TIMESTAMPTZ,
				    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
				    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
				)
				""");
		jdbcTemplate.update(
				"""
				INSERT INTO legacy_ci."user"
				    (id, username, password_hash)
				VALUES (?::uuid, ?, ?)
				""",
				"22222222-2222-4222-8222-222222222222",
				"legacy@example.com",
				"legacy-password");

		Flyway.configure()
				.dataSource(
						POSTGRES.getJdbcUrl(),
						POSTGRES.getUsername(),
						POSTGRES.getPassword())
				.schemas(schema)
				.defaultSchema(schema)
				.locations("classpath:db/migration")
				.baselineOnMigrate(true)
				.baselineVersion("0")
				.load()
				.migrate();

		assertEquals(
				1,
				jdbcTemplate.queryForObject(
						"SELECT COUNT(*) FROM legacy_ci.\"user\" WHERE username = 'legacy@example.com'",
						Integer.class));
		assertEquals(
				List.of("0", "1", "2", "3", "4", "5", "6", "7"),
				jdbcTemplate.queryForList(
						"""
						SELECT version
						FROM legacy_ci.flyway_schema_history
						WHERE version IS NOT NULL
						ORDER BY installed_rank
						""",
						String.class));
		assertFalse(jdbcTemplate.queryForList(
				"""
				SELECT table_name
				FROM information_schema.tables
				WHERE table_schema = 'legacy_ci'
				  AND table_name = 'scene'
				""",
				String.class).isEmpty());
		assertFalse(jdbcTemplate.queryForList(
				"""
				SELECT table_name
				FROM information_schema.tables
				WHERE table_schema = 'legacy_ci'
				  AND table_name = 'interview_report'
				""",
				String.class).isEmpty());

		jdbcTemplate.execute("DROP SCHEMA " + schema + " CASCADE");
	}

	private List<String> columnDefinitions(String tableName) {
		return jdbcTemplate.queryForList(
				"""
				SELECT CONCAT_WS(
				           '|',
				           column_name,
				           data_type,
				           COALESCE(character_maximum_length::TEXT, ''),
				           COALESCE(numeric_precision::TEXT, ''),
				           COALESCE(numeric_scale::TEXT, ''),
				           is_nullable)
				FROM information_schema.columns
				WHERE table_schema = 'public'
				  AND table_name = ?
				ORDER BY ordinal_position
				""",
				String.class,
				tableName);
	}

	private void insertInterview(
			String interviewId,
			String sessionId,
			String difficulty,
			Integer recordingDurationSeconds) {
		jdbcTemplate.update(
				"""
				INSERT INTO interview
				    (id, user_id, session_id, job_title, difficulty, role_summary,
				     recording_duration_seconds)
				VALUES (?, '11111111-1111-4111-8111-111111111111', ?,
				        'Product Manager', ?, '{}'::JSONB, ?)
				""",
				interviewId,
				sessionId,
				difficulty,
				recordingDurationSeconds);
	}

	private InterviewQuestionRecord question(
			String interviewId,
			int questionNo,
			InterviewQuestionType questionType,
			OffsetDateTime createdAt) {
		return new InterviewQuestionRecord(
				interviewId,
				questionNo,
				questionType,
				"Question " + questionNo,
				createdAt,
				createdAt);
	}

	private InterviewReportRecord interviewReport(
			String interviewId,
			OffsetDateTime createdAt) {
		return new InterviewReportRecord(
				interviewId,
				InterviewReportType.FULL,
				new BigDecimal("88.5"),
				"整体表达清晰，岗位语境适切。",
				interviewDimension("81.1", "流利度"),
				interviewDimension("82.2", "逻辑与连贯性"),
				interviewDimension("83.3", "语法控制"),
				interviewDimension("84.4", "发音可理解度"),
				interviewDimension("85.5", "词汇与面试表达"),
				createdAt,
				createdAt);
	}

	private InterviewReportDimension interviewDimension(
			String score,
			String name) {
		return new InterviewReportDimension(
				new BigDecimal(score),
				name + "评价",
				name + "行动建议");
	}

	private void insertReport(String interviewId, String reportType) {
		jdbcTemplate.update(
				"""
				INSERT INTO interview_report (
				    interview_id, report_type, overall_score, overall_summary,
				    fluency_score, fluency_evaluation, fluency_action_suggestion,
				    logic_coherence_score, logic_coherence_evaluation,
				    logic_coherence_action_suggestion,
				    grammar_control_score, grammar_control_evaluation,
				    grammar_control_action_suggestion,
				    pronunciation_intelligibility_score,
				    pronunciation_intelligibility_evaluation,
				    pronunciation_intelligibility_action_suggestion,
				    vocabulary_expression_score, vocabulary_expression_evaluation,
				    vocabulary_expression_action_suggestion)
				VALUES (
				    ?, ?, 80.0, 'summary',
				    80.0, 'evaluation', 'action',
				    80.0, 'evaluation', 'action',
				    80.0, 'evaluation', 'action',
				    80.0, 'evaluation', 'action',
				    80.0, 'evaluation', 'action')
				""",
				interviewId,
				reportType);
	}

	private CustomSceneDefinition sceneDefinition() {
		return new CustomSceneDefinition(
				"custom_it1",
				"11111111-1111-4111-8111-111111111111",
				"酒店入住",
				"酒店前台",
				"前台接待员",
				"住客",
				"完成入住",
				"保持礼貌",
				"{\"minimum_user_turns\":2}",
				List.of(new LearningContentItem(
						"word_it1",
						"reservation",
						"预订",
						"/ˌrezərˈveɪʃn/")),
				List.of(new LearningContentItem(
						"phrase_it1",
						"check in",
						"办理入住",
						"/tʃek ɪn/")),
				List.of(new LearningContentItem(
						"sentence_it1",
						"I have a reservation.",
						"我有预订。",
						"")));
	}

	private CustomTurnEvaluation turnEvaluation(
			int turnNo,
			BigDecimal overallScore) {
		return new CustomTurnEvaluation(
				"custom_it1",
				"session_it1",
				turnNo,
				"I would like some coffee.",
				overallScore,
				new BigDecimal("82"),
				new BigDecimal("80"),
				new BigDecimal("100"),
				new BigDecimal("86"),
				new BigDecimal("83"),
				"表达清楚。",
				"I'd like some coffee, please.",
				List.of(new PronunciationWordDetail(
						0,
						"coffee",
						new BigDecimal("88"),
						List.of(new PronunciationWordDetail.Phoneme(
								0,
								"k",
								"k",
								new BigDecimal("90"),
								0,
								1)))));
	}

	private PronunciationAssessmentResult pronunciationAssessment() {
		return new PronunciationAssessmentResult(
				new BigDecimal("88"),
				new BigDecimal("87"),
				new BigDecimal("86"),
				new BigDecimal("100"),
				new BigDecimal("89"),
				new BigDecimal("90"),
				EndingTone.FALL,
				List.of(new PronunciationWordResult(
						0,
						"reservation",
						WordReadStatus.NORMAL,
						new BigDecimal("88"),
						new BigDecimal("89"),
						true,
						List.of(new PronunciationPhonemeResult(
								0,
								"r",
								"r",
								new BigDecimal("90"),
								0,
								1)))));
	}
}
