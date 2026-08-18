package com.unispeaking.admin.usage.web;

import com.unispeaking.admin.usage.application.AiInvocationQueryService;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai/usage")
public final class AiInvocationController {
	private final AiInvocationQueryService service;

	public AiInvocationController(AiInvocationQueryService service) { this.service = service; }

	@GetMapping
	AiInvocationQueryService.UsageResponse usage(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
			@RequestParam(required = false) String userId,
			@RequestParam(required = false) String providerId,
			@RequestParam(required = false) String modelId,
			@RequestParam(defaultValue = "1") Integer page,
			@RequestParam(required = false) Integer limit) {
		return service.query(new AiInvocationQueryService.Query(from, to, userId, providerId, modelId, limit), page);
	}
}
