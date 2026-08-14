package com.unispeaking.common.exception;

/** Authentication failure raised by the email identity flow. */
public class EmailAuthException extends RuntimeException {
	public EmailAuthException(String code) {
		super(code);
	}
}
