package com.unispeaking.provider;

import java.util.function.Supplier;

/** Internal server-side credential override; never populated from a client token. */
public final class ProviderCredentialOverride {
	private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

	private ProviderCredentialOverride() {}

	public static String currentOr(String fallback) {
		String value = CURRENT.get();
		return value == null || value.isBlank() ? fallback : value;
	}

	static <T> T call(String credential, Supplier<T> operation) {
		String previous = CURRENT.get();
		try {
			if (credential == null || credential.isBlank()) CURRENT.remove(); else CURRENT.set(credential);
			return operation.get();
		}
		finally {
			if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
		}
	}
}
