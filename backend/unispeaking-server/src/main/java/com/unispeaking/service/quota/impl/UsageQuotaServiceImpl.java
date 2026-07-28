package com.unispeaking.service.quota.impl;

import com.unispeaking.service.quota.UsageQuotaService;
import org.springframework.stereotype.Service;

@Service
public class UsageQuotaServiceImpl implements UsageQuotaService {
	@Override
	public void reserve(String userId, String localSessionId) {
	}

	@Override
	public void startMetering(String localSessionId) {
	}

	@Override
	public void settle(String localSessionId) {
	}

	@Override
	public void settleReservedQuota(String localSessionId) {
	}
}
