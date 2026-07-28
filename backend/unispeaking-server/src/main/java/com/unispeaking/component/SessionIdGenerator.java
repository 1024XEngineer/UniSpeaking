package com.unispeaking.component;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SessionIdGenerator {

	public String generate() {
		return "scene_" + UUID.randomUUID();
	}
}
