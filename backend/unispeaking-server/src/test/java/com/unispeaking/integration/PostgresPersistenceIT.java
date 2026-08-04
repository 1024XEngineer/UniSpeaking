package com.unispeaking.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.domain.po.scene.CustomSceneDefinition;
import com.unispeaking.infrastructure.persistence.entity.evaluation.CustomTurnEvaluation;
import com.unispeaking.infrastructure.persistence.entity.evaluation.PronunciationWordDetail;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SceneSentenceReadingRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.SessionEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.evaluation.TurnEvaluationRepository;
import com.unispeaking.infrastructure.persistence.repository.scene.MybatisSceneRepository;
import com.unispeaking.infrastructure.persistence.repository.session.SessionMessageRepository;
import com.unispeaking.infrastructure.persistence.repository.user.MybatisUserAccountRepository;
import com.unispeaking.infrastructure.persistence.repository.user.MybatisUserProfileRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
	private MybatisSceneRepository sceneRepository;

	@Autowired
	private SessionMessageRepository sessionMessageRepository;

	@Autowired
	private TurnEvaluationRepository turnEvaluationRepository;

	@Autowired
	private SessionEvaluationRepository sessionEvaluationRepository;

	@Autowired
	private SceneSentenceReadingRepository sentenceReadingRepository;

	@BeforeEach
	void clearBusinessTables() {
		jdbcTemplate.execute("""
				TRUNCATE TABLE
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
		Integer migrationCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE success",
				Integer.class);
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
		String successFactorType = jdbcTemplate.queryForObject(
				"""
				SELECT data_type
				FROM information_schema.columns
				WHERE table_schema = 'public'
				  AND table_name = 'scene'
				  AND column_name = 'success_factor'
				""",
				String.class);

		assertEquals(3, migrationCount);
		assertEquals(303, topicCount);
		assertEquals(1771, questionCount);
		assertEquals(0, questionLikeTitleCount);
		assertEquals("jsonb", successFactorType);
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
				List.of("0", "1", "2", "3"),
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
