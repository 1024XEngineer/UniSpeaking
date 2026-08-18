package com.unispeaking.provider.usage;

public interface AiInvocationLedger {
	void record(AiInvocationAttempt attempt);
}
