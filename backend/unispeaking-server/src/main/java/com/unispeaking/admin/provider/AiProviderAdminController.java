package com.unispeaking.admin.provider;

import com.unispeaking.admin.provider.AiProviderAdminService.ConfigurationResponse;
import com.unispeaking.admin.provider.AiProviderAdminService.ModelView;
import com.unispeaking.admin.provider.AiProviderAdminService.ProviderView;
import com.unispeaking.admin.provider.AiProviderAdminService.RouteView;
import com.unispeaking.admin.provider.AiProviderAdminService.UpdateModelRequest;
import com.unispeaking.admin.provider.AiProviderAdminService.UpdateProviderRequest;
import com.unispeaking.admin.provider.AiProviderAdminService.UpdateRouteRequest;
import com.unispeaking.admin.provider.AiProviderAdminService.UpdateCredentialRequest;
import com.unispeaking.provider.config.AiProviderCredentialStore;
import com.unispeaking.domain.vo.provider.AiCapability;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai")
public final class AiProviderAdminController {
	private final AiProviderAdminService service;

	public AiProviderAdminController(AiProviderAdminService service) {
		this.service = service;
	}

	@GetMapping("/configuration")
	ConfigurationResponse configuration() { return service.configuration(); }

	@PatchMapping("/providers/{providerId}")
	ProviderView updateProvider(@PathVariable String providerId, @RequestBody UpdateProviderRequest request) {
		return service.updateProvider(providerId, request);
	}

	@PatchMapping("/models/{modelId}")
	ModelView updateModel(@PathVariable String modelId, @RequestBody UpdateModelRequest request) {
		return service.updateModel(modelId, request);
	}

	@PutMapping("/routes/{capability}")
	RouteView updateRoute(@PathVariable AiCapability capability, @RequestBody UpdateRouteRequest request) {
		return service.replaceRoute(capability, request);
	}

	@GetMapping("/providers/{providerId}/credential")
	AiProviderCredentialStore.CredentialStatus credential(@PathVariable String providerId) {
		return service.credentialStatus(providerId);
	}

	@PutMapping("/providers/{providerId}/credential")
	AiProviderCredentialStore.CredentialStatus replaceCredential(
			@PathVariable String providerId,
			@RequestBody UpdateCredentialRequest request) {
		return service.replaceCredential(providerId, request.secret());
	}
}
