package com.unispeaking.provider.config;

public interface AiProviderCredentialStore {
	String credentialOrFallback(String providerId, String fallback);
	CredentialStatus status(String providerId);
	CredentialStatus replace(String providerId, String plaintext);

	record CredentialStatus(boolean configured, String fingerprint, boolean writable) {}
}
