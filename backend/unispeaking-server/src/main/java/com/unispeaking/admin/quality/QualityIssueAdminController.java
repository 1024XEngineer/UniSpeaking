package com.unispeaking.admin.quality;

import com.unispeaking.admin.auth.domain.AdminAccount;
import com.unispeaking.admin.quality.QualityIssueAdminService.CreateIssueRequest;
import com.unispeaking.admin.quality.QualityIssueAdminService.IssueEventsResponse;
import com.unispeaking.admin.quality.QualityIssueAdminService.IssueListResponse;
import com.unispeaking.admin.quality.QualityIssueAdminService.IssuePlatform;
import com.unispeaking.admin.quality.QualityIssueAdminService.IssueStatus;
import com.unispeaking.admin.quality.QualityIssueAdminService.IssueType;
import com.unispeaking.admin.quality.QualityIssueAdminService.QualityIssueView;
import com.unispeaking.admin.quality.QualityIssueAdminService.QualitySummary;
import com.unispeaking.admin.quality.QualityIssueAdminService.UpdateIssueRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/quality")
public class QualityIssueAdminController {
	private final QualityIssueAdminService service;

	public QualityIssueAdminController(QualityIssueAdminService service) {
		this.service = service;
	}

	@GetMapping("/summary")
	QualitySummary summary() {
		return service.summary();
	}

	@GetMapping("/issues")
	IssueListResponse issues(
			@RequestParam(required = false) IssueStatus status,
			@RequestParam(required = false) IssuePlatform platform,
			@RequestParam(required = false) IssueType issueType,
			@RequestParam(defaultValue = "100") int limit) {
		return service.list(status, platform, issueType, limit);
	}

	@GetMapping("/issues/{issueId}")
	QualityIssueView issue(@PathVariable UUID issueId) {
		return service.get(issueId);
	}

	@GetMapping("/issues/{issueId}/events")
	IssueEventsResponse events(
			@PathVariable UUID issueId,
			@RequestParam(defaultValue = "50") int limit) {
		service.get(issueId);
		return service.events(issueId, limit);
	}

	@PostMapping("/issues")
	QualityIssueView create(
			@Valid @RequestBody CreateIssueRequest request,
			@AuthenticationPrincipal AdminAccount administrator) {
		return service.create(request, administrator.id(), administrator.login());
	}

	@PatchMapping("/issues/{issueId}")
	QualityIssueView update(
			@PathVariable UUID issueId,
			@Valid @RequestBody UpdateIssueRequest request,
			@AuthenticationPrincipal AdminAccount administrator) {
		return service.update(issueId, request, administrator.id(), administrator.login());
	}
}
