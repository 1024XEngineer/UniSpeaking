package com.unispeaking.domain.dto.scene;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record UpdateIeltsSettingsRequest(
		@DecimalMin("0.0") @DecimalMax("9.0") BigDecimal targetScore,
		String examinerId) {
}
