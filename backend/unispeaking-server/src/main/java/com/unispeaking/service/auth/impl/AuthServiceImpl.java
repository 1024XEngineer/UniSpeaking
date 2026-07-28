package com.unispeaking.service.auth.impl;

import com.unispeaking.service.auth.AuthService;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

	private static final String LOCAL_DEVELOPMENT_USER_ID = "local-demo-user";

	@Override
	public String requireUserId(String requestedUserId) {
		if (requestedUserId == null || requestedUserId.isBlank()) {
			return LOCAL_DEVELOPMENT_USER_ID;
		}
		return requestedUserId.trim();
	}
}
