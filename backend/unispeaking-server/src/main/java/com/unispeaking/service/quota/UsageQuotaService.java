package com.unispeaking.service.quota;

public interface UsageQuotaService {
	void reserve(String userId, String localSessionId);
	void startMetering(String localSessionId);
	void settle(String localSessionId);
	void settleReservedQuota(String localSessionId);
}
