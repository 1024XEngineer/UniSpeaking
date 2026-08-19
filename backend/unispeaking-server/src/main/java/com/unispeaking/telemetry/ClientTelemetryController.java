package com.unispeaking.telemetry;

import com.unispeaking.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/telemetry")
public class ClientTelemetryController {

	private final ClientTelemetryService telemetryService;

	public ClientTelemetryController(ClientTelemetryService telemetryService) {
		this.telemetryService = telemetryService;
	}

	@PostMapping("/events")
	public ApiResponse<ClientTelemetryAcceptedResponse> collect(
			@Valid @RequestBody ClientTelemetryBatchRequest batch,
			@AuthenticationPrincipal Jwt jwt) {
		String userId = jwt == null ? null : jwt.getSubject();
		return ApiResponse.success(new ClientTelemetryAcceptedResponse(
				telemetryService.accept(batch, userId)));
	}

	public record ClientTelemetryAcceptedResponse(int accepted) {
	}
}
