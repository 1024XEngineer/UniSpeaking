package com.unispeaking.telemetry;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ClientTelemetryBatchRequest(
		@NotEmpty @Size(max = 20) List<@Valid ClientTelemetryEventRequest> events) {
}
