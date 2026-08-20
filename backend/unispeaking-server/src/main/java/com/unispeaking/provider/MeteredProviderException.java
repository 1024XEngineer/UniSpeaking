package com.unispeaking.provider;

import com.unispeaking.common.exception.BusinessException;

/** Provider failure that occurred after the upstream request became billable. */
public final class MeteredProviderException extends BusinessException {

	private final Boolean retryable;
	private final String providerRequestId;
	private final ProviderUsage usage;

	public MeteredProviderException(
			String code,
			String message,
			Boolean retryable,
			String providerRequestId,
			ProviderUsage usage) {
		super(code, message);
		this.retryable = retryable;
		this.providerRequestId = providerRequestId;
		this.usage = usage;
	}

	public Boolean retryable() {
		return retryable;
	}

	public String providerRequestId() {
		return providerRequestId;
	}

	public ProviderUsage usage() {
		return usage;
	}
}
