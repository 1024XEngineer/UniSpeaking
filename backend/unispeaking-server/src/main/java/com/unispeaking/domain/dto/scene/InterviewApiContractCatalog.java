package com.unispeaking.domain.dto.scene;

import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

public final class InterviewApiContractCatalog {

	private static final String ROOT = "/api/interviews";

	private static final List<InterviewEndpointContract> ENDPOINTS = List.of(
			endpoint(HttpMethod.POST, ROOT + "/job-description/ocr", HttpStatus.OK,
					Void.class, InterviewJobDescriptionOcrResponse.class),
			endpoint(HttpMethod.POST, ROOT, HttpStatus.CREATED,
					CreateInterviewRequest.class, CreateInterviewResponse.class),
			endpoint(HttpMethod.POST, ROOT + "/{id}/answers", HttpStatus.ACCEPTED,
					InterviewAnswerRequest.class, InterviewAnswerAcceptedResponse.class),
			endpoint(HttpMethod.GET, ROOT + "/{id}/state", HttpStatus.OK,
					Void.class, InterviewStateResponse.class),
			endpoint(HttpMethod.POST, ROOT + "/{id}/heartbeat", HttpStatus.OK,
					Void.class, InterviewHeartbeatResponse.class),
			endpoint(HttpMethod.POST, ROOT + "/{id}/end", HttpStatus.ACCEPTED,
					EndInterviewRequest.class, EndInterviewResponse.class),
			endpoint(HttpMethod.GET, ROOT, HttpStatus.OK,
					Void.class, InterviewHistoryResponse.class),
			endpoint(HttpMethod.GET, ROOT + "/{id}", HttpStatus.OK,
					Void.class, InterviewDetailResponse.class),
			endpoint(HttpMethod.GET, ROOT + "/{id}/recording", HttpStatus.OK,
					Void.class, InterviewRecordingResponse.class),
			endpoint(HttpMethod.GET, ROOT + "/trends", HttpStatus.OK,
					Void.class, InterviewTrendResponse.class),
			endpoint(HttpMethod.POST, ROOT + "/{sourceId}/repractice", HttpStatus.CREATED,
					Void.class, CreateInterviewResponse.class),
			endpoint(HttpMethod.DELETE, ROOT + "/{id}", HttpStatus.OK,
					Void.class, DeleteInterviewResponse.class));

	private InterviewApiContractCatalog() {
	}

	public static List<InterviewEndpointContract> endpoints() {
		return ENDPOINTS;
	}

	private static InterviewEndpointContract endpoint(
			HttpMethod method,
			String path,
			HttpStatus status,
			Class<?> requestType,
			Class<?> responseType) {
		return new InterviewEndpointContract(method, path, status, requestType, responseType);
	}
}
