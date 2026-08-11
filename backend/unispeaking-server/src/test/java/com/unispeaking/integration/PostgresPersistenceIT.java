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
import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.dto.evaluation.DialogueReportResult;
import com.unispeaking.domain.dto.scene.LearningContentItem;
import com.unispeaking.domain.dto.scene.SceneGenerationResponse;
import com.unispeaking.domain.dto.session.Message;
import com.unispeaking.domain.po.auth.UserAccount;
import com.unispeaking.domain.po.auth.UserRole;
import com.unispeaking.domain.po.auth.UserStatus;
import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.domain.po.profile.WeeklyLearningGoals;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.domain.po.scene.InterviewSceneDefinition;
import com.unispeaking.domain.vo.scene.InterviewDifficulty;
import com.unispeaking.domain.vo.scene.TargetRoleSummary;
import com.unispeaking.infrastructure.persistence.entity.evaluation.CustomTurnEvaluation;
import com.unispeaking.infrastructure.persistence.entity.evaluation.PronunciationWordDetail;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SceneSentenceReadingRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SessionEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.TurnEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.InterviewSceneRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.MybatisSceneRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.infrastructure.persistence.repository.session.PracticeSessionRepository;
import com.unispeaking.admin.usage.adapters.jdbc.JdbcOfficialUsageSink;
import com.unispeaking.admin.usage.domain.ModelUsage;
import com.unispeaking.admin.usage.domain.OfficialUsageRecord;
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
				() -> "com.unispeaking.common.persistence.typehandler");
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
	private SessionMessageRepository sessionMessageRepository;

	@Autowired
	private PracticeSessionRepository practiceSessionRepository;

	@Autowired
	private TurnEvaluationRepository turnEvaluationRepository;

	@Autowired
	private SessionEvaluationRepository sessionEvaluationRepository;

	@Autowired
	private SceneSentenceReadingRepository sentenceReadingRepository;

	@Autowired
	private InterviewSceneRepository interviewSceneRepository;

	@BeforeEach
	void clearBusinessTables() {
		jdbcTemplate.execute("""
				TRUNCATE TABLE
				    official_usage_records,
				    auth_email_challenges,
				    user_sessions,
				    admin_sessions,
				    user_entitlements,
				    app_users,
				    admin_accounts,
				    user_feedback,
				    ielts_part_evaluation,
				    ielts_evaluation,
				    ielts,
				    user_ielts,
				    practice_session,
				    sentence_evaluation,
				    session_evaluation,
				    turn_evaluation,
				    session_message,
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

		assertEquals(List.of("1", "2", "9", "10", "11", "12", "13"), migrationVersions);
		assertEquals(303, topicCount);
		assertEquals(1771, questionCount);
		assertEquals(0, questionLikeTitleCount);
		assertEquals(3, helpTableCount);
		assertEquals("jsonb", successFactorType);
		assertEquals(
				1,
				jdbcTemplate.queryForObject(
						"""
						SELECT COUNT(*)
						FROM information_schema.columns
						WHERE table_schema = 'public'
						  AND table_name = 'practice_session'
						  AND column_name = 'provider_session_id'
						""",
						Integer.class));
		assertTrue(jdbcTemplate.queryForObject(
				"""
				SELECT indexdef LIKE 'CREATE UNIQUE INDEX%'
				FROM pg_indexes
				WHERE schemaname = 'public'
				  AND indexname = 'idx_practice_session_provider_session_id'
				""",
				Boolean.class));
		assertEquals(
				1,
				jdbcTemplate.queryForObject(
						"""
						SELECT COUNT(*)
						FROM information_schema.tables
						WHERE table_schema = 'public'
						  AND table_name = 'official_usage_records'
						""",
						Integer.class));
	}

	@Test
	void persistsIeltsUserContentAndPartEvaluation() {
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
				     topic_selection_method, part1_topic_id, content)
				VALUES ('session_ielts_it1', ?::uuid, 'PART_PRACTICE', 'PART_1',
				        'ielts_group_it1', 'USER_SELECTED', 'ielts_group_it1',
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
				INSERT INTO ielts_part_evaluation
				    (part_evaluation_id, session_id, ielts_id, part,
				     fluency_coherence_score,
				     lexical_resource_score,
				     grammatical_range_accuracy_score,
				     pronunciation_score, summary, strengths, improvements,
				     evaluation_status, completed_at)
				VALUES ('ielts_part_session_ielts_it1', 'session_ielts_it1',
				        'session_ielts_it1', 'PART_1', 7.5, 7.0, 6.5, 7.0,
				        '表达清晰，细节可以更充分。', ARRAY['词汇自然'],
				        ARRAY['补充例子'], 'COMPLETED', CURRENT_TIMESTAMP)
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
				new BigDecimal("7.5"),
				jdbcTemplate.queryForObject(
						"""
						SELECT fluency_coherence_score
						FROM ielts_part_evaluation
						WHERE session_id = 'session_ielts_it1'
						""",
						BigDecimal.class));
		assertEquals(
				"session_ielts_it1",
				jdbcTemplate.queryForObject(
						"""
						SELECT ielts_id
						FROM ielts_part_evaluation
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
	void bindsProviderSessionAndMatchesOfficialSlsUsageWithoutCrossUserAmbiguity() {
		UUID firstUserId = UUID.randomUUID();
		UUID secondUserId = UUID.randomUUID();
		jdbcTemplate.update(
				"insert into \"user\" (id, username, password_hash) values (?::uuid, ?, 'hash')",
				firstUserId.toString(), "sls-first@example.com");
		jdbcTemplate.update(
				"insert into \"user\" (id, username, password_hash) values (?::uuid, ?, 'hash')",
				secondUserId.toString(), "sls-second@example.com");
		jdbcTemplate.update(
				"""
				insert into practice_session
				    (session_id, user_id, scene_type, status, started_at)
				values ('local-sls-1', ?::uuid, 'FREE_CHAT', 'ACTIVE', current_timestamp),
				       ('local-sls-2', ?::uuid, 'FREE_CHAT', 'ACTIVE', current_timestamp)
				""",
				firstUserId.toString(), secondUserId.toString());

		practiceSessionRepository.bindProviderSession("local-sls-1", firstUserId, "sess_qwen_sls_1");
		var imported = new JdbcOfficialUsageSink(jdbcTemplate).importRecords(List.of(
				new OfficialUsageRecord(
						"request-sls-1", "sess_qwen_sls_1", 1000, 2500, "200",
						"qwen3.5-omni-flash-realtime", "workspace", "apikey", "webrtc",
						new ModelUsage(1, 100, 70, 30, 20, 50, 10, 20))));

		assertEquals(1, imported.matched());
		assertEquals("sess_qwen_sls_1", jdbcTemplate.queryForObject(
				"select provider_session_id from practice_session where session_id = 'local-sls-1'",
				String.class));
		BusinessException duplicateBinding = assertThrows(BusinessException.class,
				() -> practiceSessionRepository.bindProviderSession(
						"local-sls-2", secondUserId, "sess_qwen_sls_1"));
		assertEquals("PRACTICE_SESSION_PERSISTENCE_FAILED", duplicateBinding.code());
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
				"SELECT preferences ->> 'weekly_duration_target_minutes' "
						+ "FROM user_preference WHERE user_id = ?",
				String.class,
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
				new BigDecimal("88.00"),
				sessionEvaluationRepository.findScoreSnapshotsBySessionIds(
						List.of("session_it1"))
						.getFirst()
						.accuracy());
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
	void roundTripsInterviewSceneWithJsonbAndOwnershipFilter() {
		String userId = "00000000-0000-0000-0000-000000000001";
		InterviewSceneDefinition definition = new InterviewSceneDefinition(
				"interview_it1",
				userId,
				"{\"jobTitle\":\"Java Engineer\",\"responsibilities\":[\"build services\"],\"qualificationRequirements\":[\"Java 21\"]}",
				"Java Engineer 岗位职责与任职要求",
				"{\"candidateOverview\":\"candidate overview\",\"roleOverview\":\"role overview\",\"interviewTopics\":[\"自我介绍\",\"经历与项目\",\"团队协作\",\"职业规划\"]}",
				InterviewDifficulty.STANDARD,
				"interview system prompt",
				null,
				null,
				null);
		interviewSceneRepository.save(definition);

		InterviewSceneDefinition loaded = interviewSceneRepository
				.findById("interview_it1")
				.orElseThrow();
		assertEquals("interview_it1", loaded.sceneId());
		assertEquals(userId, loaded.userId());
		assertEquals(InterviewDifficulty.STANDARD, loaded.difficulty());
		assertEquals("interview system prompt", loaded.scenePrompt());
		assertTrue(loaded.confirmedMaterialJson().contains("Java Engineer"));
		assertTrue(loaded.interviewContextJson().contains("自我介绍"));
		assertNull(loaded.deletedAt());
		assertEquals("object", jdbcTemplate.queryForObject(
				"SELECT jsonb_typeof(confirmed_material) FROM interview_scene WHERE scene_id = ?",
				String.class,
				"interview_it1"));
		// 归属辅助：非所有者查不到；软删后查不到。
		assertTrue(interviewSceneRepository.findOwnedById("interview_it1", userId).isPresent());
		assertTrue(interviewSceneRepository.findOwnedById("interview_it1", "other-user").isEmpty());
		jdbcTemplate.update(
				"UPDATE interview_scene SET deleted_at = CURRENT_TIMESTAMP WHERE scene_id = ?",
				"interview_it1");
		assertTrue(interviewSceneRepository.findById("interview_it1").isEmpty());
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
				List.of("0", "1", "2", "9", "10", "11", "12", "13"),
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
