package com.unispeaking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.unispeaking.service.auth.impl.AuthServiceImpl;
import org.junit.jupiter.api.Test;

class AuthServiceImplTest {

	@Test
	void usesLocalDevelopmentUserWhenLoginIsUnavailable() {
		var service = new AuthServiceImpl();

		assertEquals("local-demo-user", service.requireUserId(null));
		assertEquals("local-demo-user", service.requireUserId(" "));
		assertEquals("provided-user", service.requireUserId(" provided-user "));
	}
}
