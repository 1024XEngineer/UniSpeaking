package com.unispeaking.domain.dto.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAccountProfileRequest(
		@NotBlank
		@Size(max = 32)
		String nickname) {
}
