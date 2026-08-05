package com.unispeaking.domain.dto.scene;

import java.util.Objects;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

public record InterviewEndpointContract(
		HttpMethod method,
		String path,
		HttpStatus successStatus,
		Class<?> requestType,
		Class<?> responseType) {

	public InterviewEndpointContract {
		Objects.requireNonNull(method, "method");
		if (path == null || path.isBlank()) {
			throw new IllegalArgumentException("path must not be blank");
		}
		Objects.requireNonNull(successStatus, "successStatus");
		Objects.requireNonNull(requestType, "requestType");
		Objects.requireNonNull(responseType, "responseType");
	}
}
