package com.unispeaking.provider;

import java.util.function.Supplier;

/** Scoped context propagation for background work without changing stable provider client APIs. */
public final class AiInvocationContexts {
	private static final ThreadLocal<AiInvocationContext> CURRENT = new ThreadLocal<>();

	private AiInvocationContexts() {}

	public static AiInvocationContext current() {
		return CURRENT.get();
	}

	public static <T> T call(AiInvocationContext context, Supplier<T> operation) {
		AiInvocationContext previous = CURRENT.get();
		try {
			if (context == null) CURRENT.remove(); else CURRENT.set(context);
			return operation.get();
		}
		finally {
			if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
		}
	}
}
