package com.unispeaking.provider;

import java.util.Map;
import java.util.function.Supplier;

/** Internal server-side credential override; never populated from a client token. */
public final class ProviderCredentialOverride {
	private static final ThreadLocal<Map<String, String>> CURRENT = new ThreadLocal<>();

	private ProviderCredentialOverride() {}

	public static String currentOr(String fallback) {
		return currentOr("apiKey", fallback);
	}

	public static String currentOr(String field, String fallback) {
		Map<String, String> values = CURRENT.get();
		String value = values == null ? null : values.get(field);
		return value == null || value.isBlank() ? fallback : value;
	}

	static <T> T call(Map<String, String> credentials, Supplier<T> operation) {
		Map<String, String> previous = CURRENT.get();
		try {
			if (credentials == null || credentials.isEmpty()) CURRENT.remove();
			else CURRENT.set(Map.copyOf(credentials));
			return operation.get();
		}
		finally {
			if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
		}
	}
}
