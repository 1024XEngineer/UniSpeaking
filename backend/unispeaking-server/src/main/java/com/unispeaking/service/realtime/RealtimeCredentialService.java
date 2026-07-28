package com.unispeaking.service.realtime;

import com.unispeaking.domain.vo.realtime.ProviderType;
import com.unispeaking.domain.vo.realtime.RealtimeCredential;

public interface RealtimeCredentialService {
	RealtimeCredential getCredential(ProviderType providerType);
}
