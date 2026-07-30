package com.unispeaking.domain.dto.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReactivateAccountRequest(
		@NotBlank(message = "不能为空")
		@Email(message = "格式不正确")
		@Size(max = 254, message = "不能超过 254 个字符")
		String username,
		@NotBlank(message = "不能为空")
		@Size(min = 6, max = 72, message = "长度必须为 6 到 72 位")
		String password) {
}
