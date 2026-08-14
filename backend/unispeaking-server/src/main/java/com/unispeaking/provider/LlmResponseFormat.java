package com.unispeaking.provider;

/**
 * Optional response contract requested by an LLM caller.
 * Providers that do not support a format must preserve their normal behavior.
 */
public enum LlmResponseFormat {
	TEXT,
	JSON_OBJECT
}
