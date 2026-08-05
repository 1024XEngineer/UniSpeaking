package com.unispeaking.domain.dto.scene;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.domain.vo.scene.InterviewDifficulty;
import com.unispeaking.domain.vo.scene.InterviewQuestionType;
import com.unispeaking.domain.vo.scene.InterviewReportType;
import com.unispeaking.domain.vo.scene.TargetRoleSummary;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

class InterviewApiContractTest {

	@Test
	void freezesAnswerAndStatePollingFields() {
		assertArrayEquals(
				new String[] {"submissionId", "questionNo"},
				fields(InterviewAnswerRequest.class));
		assertArrayEquals(
				new String[] {"submission", "statePath"},
				fields(InterviewAnswerAcceptedResponse.class));
		assertArrayEquals(
				new String[] {
					"submissionId", "questionNo", "processingStatus", "retryable",
					"errorCode", "message", "updatedAt"
				},
				fields(InterviewSubmissionResponse.class));
		assertArrayEquals(
				new String[] {
					"interviewId", "sessionId", "status", "errorCode", "message",
					"retryable", "currentQuestionNo",
					"acceptingSubmissions", "endRequested", "confirmationRequired",
					"actualWords", "minimumWords", "lastSeen", "latestSubmission",
					"nextQuestion"
				},
				fields(InterviewStateResponse.class));
		assertArrayEquals(
				new InterviewProcessingStatus[] {
					InterviewProcessingStatus.ACCEPTED,
					InterviewProcessingStatus.PROCESSING,
					InterviewProcessingStatus.COMPLETED,
					InterviewProcessingStatus.FAILED
				},
				InterviewProcessingStatus.values());
	}

	@Test
	void freezesEndConfirmationAndWaitingContract() {
		assertArrayEquals(
				new String[] {"confirmInsufficientData"},
				fields(EndInterviewRequest.class));
		assertArrayEquals(
				new InterviewEndStatus[] {
					InterviewEndStatus.WAITING_FOR_SUBMISSIONS,
					InterviewEndStatus.CONFIRMATION_REQUIRED,
					InterviewEndStatus.FINALIZING,
					InterviewEndStatus.COMPLETED,
					InterviewEndStatus.FAILED
				},
				InterviewEndStatus.values());
		assertArrayEquals(
				new String[] {
					"interviewId", "processingStatus", "reportType",
					"confirmationRequired", "actualWords", "minimumWords"
				},
				fields(EndInterviewResponse.class));
	}

	@Test
	void freezesAllTwelveEndpointMappingsAndReuseDecisions() {
		List<InterviewEndpointContract> endpoints = InterviewApiContractCatalog.endpoints();

		assertEquals(12, endpoints.size());
		assertEndpoint(endpoints, HttpMethod.POST, "/api/interviews/job-description/ocr",
				HttpStatus.OK, Void.class, InterviewJobDescriptionOcrResponse.class);
		assertEndpoint(endpoints, HttpMethod.POST, "/api/interviews",
				HttpStatus.CREATED, CreateInterviewRequest.class, CreateInterviewResponse.class);
		assertEndpoint(endpoints, HttpMethod.POST, "/api/interviews/{id}/answers",
				HttpStatus.ACCEPTED, InterviewAnswerRequest.class,
				InterviewAnswerAcceptedResponse.class);
		assertEndpoint(endpoints, HttpMethod.GET, "/api/interviews/{id}/state",
				HttpStatus.OK, Void.class, InterviewStateResponse.class);
		assertEndpoint(endpoints, HttpMethod.POST, "/api/interviews/{id}/heartbeat",
				HttpStatus.OK, Void.class, InterviewHeartbeatResponse.class);
		assertEndpoint(endpoints, HttpMethod.POST, "/api/interviews/{id}/end",
				HttpStatus.ACCEPTED, EndInterviewRequest.class, EndInterviewResponse.class);
		assertEndpoint(endpoints, HttpMethod.GET, "/api/interviews",
				HttpStatus.OK, Void.class, InterviewHistoryResponse.class);
		assertEndpoint(endpoints, HttpMethod.GET, "/api/interviews/{id}",
				HttpStatus.OK, Void.class, InterviewDetailResponse.class);
		assertEndpoint(endpoints, HttpMethod.GET, "/api/interviews/{id}/recording",
				HttpStatus.OK, Void.class, InterviewRecordingResponse.class);
		assertEndpoint(endpoints, HttpMethod.GET, "/api/interviews/trends",
				HttpStatus.OK, Void.class, InterviewTrendResponse.class);
		assertEndpoint(endpoints, HttpMethod.POST, "/api/interviews/{sourceId}/repractice",
				HttpStatus.CREATED, Void.class, CreateInterviewResponse.class);
		assertEndpoint(endpoints, HttpMethod.DELETE, "/api/interviews/{id}",
				HttpStatus.OK, Void.class, DeleteInterviewResponse.class);
		assertThrows(
				UnsupportedOperationException.class,
				() -> InterviewApiContractCatalog.endpoints().clear());
	}

	@Test
	void freezesRemainingPublicResponseFields() {
		assertArrayEquals(
				new String[] {"jobTitle", "difficulty", "jobDescription", "resumeText"},
				fields(CreateInterviewRequest.class));
		assertArrayEquals(
				new String[] {
					"interviewId", "sessionId", "difficulty", "status", "roleSummary",
					"firstQuestion"
				},
				fields(CreateInterviewResponse.class));
		assertArrayEquals(new String[] {"text"}, fields(InterviewJobDescriptionOcrResponse.class));
		assertArrayEquals(
				new String[] {"interviewId", "status", "lastSeen"},
				fields(InterviewHeartbeatResponse.class));
		assertArrayEquals(new String[] {"interviews"}, fields(InterviewHistoryResponse.class));
		assertArrayEquals(
				new String[] {
					"interviewId", "jobTitle", "difficulty", "reportType", "overallScore",
					"recordingDurationSeconds", "completedAt"
				},
				fields(InterviewHistoryItemResponse.class));
		assertArrayEquals(
				new String[] {
					"interviewId", "jobTitle", "difficulty", "roleSummary", "questions",
					"report", "recording", "completedAt"
				},
				fields(InterviewDetailResponse.class));
		assertArrayEquals(
				new String[] {"url", "expiresAt"},
				fields(InterviewRecordingResponse.class));
		assertArrayEquals(
				new String[] {"difficulty", "points"},
				fields(InterviewTrendResponse.class));
		assertArrayEquals(
				new String[] {
					"interviewId", "reportType", "completedAt", "overallScore", "fluency",
					"logicCoherence", "grammarControl", "pronunciationIntelligibility",
					"vocabularyExpression"
				},
				fields(InterviewTrendPointResponse.class));
		assertArrayEquals(
				new String[] {"interviewId", "deleted"},
				fields(DeleteInterviewResponse.class));
		assertArrayEquals(
				new String[] {
					"overview", "responsibilities", "requiredSkills",
					"qualificationRequirements"
				},
				fields(TargetRoleSummary.class));
	}

	@Test
	void aiQuestionUsesBase64WithAnExplicitMimeType() {
		InterviewAudioResponse audio = new InterviewAudioResponse(
				"audio/wav", "UklGRg==");
		InterviewAiQuestionResponse question = new InterviewAiQuestionResponse(
				1, InterviewQuestionType.MAIN, "Tell me about yourself.", audio);

		assertArrayEquals(
				new String[] {"mimeType", "base64"},
				fields(InterviewAudioResponse.class));
		assertEquals("UklGRg==", question.audio().base64());
		assertFalse(Arrays.stream(InterviewAudioResponse.class.getRecordComponents())
				.anyMatch(component -> component.getType() == byte[].class));
	}

	@Test
	void reportAlwaysContainsAllFiveDimensions() {
		assertArrayEquals(
				new String[] {
					"reportType", "overallScore", "overallSummary", "fluency",
					"logicCoherence", "grammarControl", "pronunciationIntelligibility",
					"vocabularyExpression"
				},
				fields(InterviewReportResponse.class));

		InterviewReportDimensionResponse dimension = new InterviewReportDimensionResponse(
				new BigDecimal("80.0"), "评价", "建议");
		InterviewReportResponse report = new InterviewReportResponse(
				InterviewReportType.PARTIAL,
				new BigDecimal("80.0"),
				"总结",
				dimension,
				dimension,
				dimension,
				dimension,
				dimension);
		assertEquals(InterviewReportType.PARTIAL, report.reportType());
	}

	@Test
	void publicCollectionsAreImmutableSnapshots() {
		List<InterviewHistoryItemResponse> historySource = new ArrayList<>();
		historySource.add(historyItem());
		InterviewHistoryResponse history = new InterviewHistoryResponse(historySource);
		historySource.clear();

		assertEquals(1, history.interviews().size());
		assertThrows(UnsupportedOperationException.class, () -> history.interviews().clear());

		List<InterviewQuestionResponse> questions = new ArrayList<>(List.of(
				new InterviewQuestionResponse(1, InterviewQuestionType.MAIN, "Question")));
		InterviewDetailResponse detail = new InterviewDetailResponse(
				"interview_1",
				"Engineer",
				InterviewDifficulty.STANDARD,
				roleSummary(),
				questions,
				report(),
				new InterviewRecordingMetadataResponse(60),
				OffsetDateTime.parse("2026-08-05T08:00:00Z"));
		questions.clear();
		assertEquals(1, detail.questions().size());
		assertThrows(UnsupportedOperationException.class, () -> detail.questions().clear());
	}

	@Test
	void responseContractsDoNotExposePrivateRuntimeOrSourceMaterial() {
		List<Class<?>> responseTypes = List.of(
				CreateInterviewResponse.class,
				InterviewJobDescriptionOcrResponse.class,
				InterviewAiQuestionResponse.class,
				InterviewAudioResponse.class,
				InterviewAnswerAcceptedResponse.class,
				InterviewSubmissionResponse.class,
				InterviewStateResponse.class,
				InterviewHeartbeatResponse.class,
				EndInterviewResponse.class,
				InterviewHistoryItemResponse.class,
				InterviewHistoryResponse.class,
				InterviewDetailResponse.class,
				InterviewQuestionResponse.class,
				InterviewRecordingMetadataResponse.class,
				InterviewRecordingResponse.class,
				InterviewReportDimensionResponse.class,
				InterviewReportResponse.class,
				InterviewTrendPointResponse.class,
				InterviewTrendResponse.class,
				DeleteInterviewResponse.class,
				TargetRoleSummary.class);
		List<String> forbidden = List.of(
				"transcript", "asr", "provider", "payload", "digest", "objectkey",
				"jobdescription", "resumetext", "resumecontent", "rawmaterial",
				"speechevaluation", "speechscore", "turnscore", "phoneme", "accuracy",
				"answeraudio", "audiobytes");

		for (Class<?> responseType : responseTypes) {
			for (String field : fields(responseType)) {
				String normalized = field.toLowerCase(Locale.ROOT);
				assertFalse(
						forbidden.stream().anyMatch(normalized::contains),
						() -> responseType.getSimpleName() + " exposes " + field);
			}
		}
	}

	@Test
	void detailRejectsIncompleteCompletedAssets() {
		OffsetDateTime completedAt = OffsetDateTime.parse("2026-08-05T08:00:00Z");
		assertThrows(NullPointerException.class, () -> new InterviewDetailResponse(
				"interview_1", "Engineer", InterviewDifficulty.STANDARD, roleSummary(),
				questions(), null, new InterviewRecordingMetadataResponse(60), completedAt));
		assertThrows(NullPointerException.class, () -> new InterviewDetailResponse(
				"interview_1", "Engineer", InterviewDifficulty.STANDARD, roleSummary(),
				questions(), report(), null, completedAt));
		assertThrows(NullPointerException.class, () -> new InterviewDetailResponse(
				"interview_1", "Engineer", InterviewDifficulty.STANDARD, roleSummary(),
				questions(), report(), new InterviewRecordingMetadataResponse(60), null));
		assertThrows(IllegalArgumentException.class, () -> new InterviewDetailResponse(
				"interview_1", "Engineer", InterviewDifficulty.STANDARD, roleSummary(),
				List.of(), report(), new InterviewRecordingMetadataResponse(60), completedAt));
		assertThrows(NullPointerException.class, () -> new InterviewDetailResponse(
				"interview_1", "Engineer", InterviewDifficulty.STANDARD, null,
				questions(), report(), new InterviewRecordingMetadataResponse(60), completedAt));
	}

	@Test
	void historyAndTrendsRejectIncompleteOrPartialAssets() {
		OffsetDateTime completedAt = OffsetDateTime.parse("2026-08-05T08:00:00Z");
		assertThrows(NullPointerException.class, () -> new InterviewHistoryItemResponse(
				"interview_1", "Engineer", InterviewDifficulty.STANDARD,
				InterviewReportType.FULL, null, 60, completedAt));
		assertThrows(NullPointerException.class, () -> new InterviewHistoryItemResponse(
				"interview_1", "Engineer", InterviewDifficulty.STANDARD,
				InterviewReportType.FULL, new BigDecimal("80.0"), 60, null));

		assertThrows(IllegalArgumentException.class, () -> trendPoint(
				InterviewReportType.PARTIAL, completedAt));
		assertThrows(NullPointerException.class, () -> trendPoint(
				InterviewReportType.FULL, null));
		assertEquals(
				InterviewReportType.FULL,
				trendPoint(InterviewReportType.FULL, completedAt).reportType());
	}

	private static InterviewHistoryItemResponse historyItem() {
		return new InterviewHistoryItemResponse(
				"interview_1",
				"Engineer",
				InterviewDifficulty.STANDARD,
				InterviewReportType.FULL,
				new BigDecimal("80.0"),
				60,
				OffsetDateTime.parse("2026-08-05T08:00:00Z"));
	}

	private static TargetRoleSummary roleSummary() {
		return new TargetRoleSummary(
				"Backend engineer",
				List.of("Build services"),
				List.of("Java"),
				List.of("Experience"));
	}

	private static InterviewReportResponse report() {
		InterviewReportDimensionResponse dimension = new InterviewReportDimensionResponse(
				new BigDecimal("80.0"), "评价", "建议");
		return new InterviewReportResponse(
				InterviewReportType.FULL,
				new BigDecimal("80.0"),
				"总结",
				dimension,
				dimension,
				dimension,
				dimension,
				dimension);
	}

	private static List<InterviewQuestionResponse> questions() {
		return List.of(new InterviewQuestionResponse(
				1, InterviewQuestionType.MAIN, "Question"));
	}

	private static InterviewTrendPointResponse trendPoint(
			InterviewReportType reportType,
			OffsetDateTime completedAt) {
		return new InterviewTrendPointResponse(
				"interview_1",
				reportType,
				completedAt,
				new BigDecimal("80.0"),
				new BigDecimal("80.0"),
				new BigDecimal("80.0"),
				new BigDecimal("80.0"),
				new BigDecimal("80.0"),
				new BigDecimal("80.0"));
	}

	private static void assertEndpoint(
			List<InterviewEndpointContract> endpoints,
			HttpMethod method,
			String path,
			HttpStatus status,
			Class<?> requestType,
			Class<?> responseType) {
		assertEquals(
				1,
				endpoints.stream().filter(endpoint -> endpoint.method() == method
						&& endpoint.path().equals(path)
						&& endpoint.successStatus() == status
						&& endpoint.requestType() == requestType
						&& endpoint.responseType() == responseType).count());
	}

	private static String[] fields(Class<?> type) {
		return Arrays.stream(type.getRecordComponents())
				.map(RecordComponent::getName)
				.toArray(String[]::new);
	}
}
