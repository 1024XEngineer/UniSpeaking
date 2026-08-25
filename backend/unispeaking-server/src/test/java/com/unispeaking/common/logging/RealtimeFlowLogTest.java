package com.unispeaking.common.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RealtimeFlowLogTest {
	@Test
	void summarizesSecretsSdpTextAndUrisAcrossBoundaries() {
		RealtimeFlowLog.info("info {}", "value");
		RealtimeFlowLog.warn("warn {}", "value");
		assertEquals("", RealtimeFlowLog.maskSecret(null));
		assertEquals("", RealtimeFlowLog.maskSecret(" "));
		assertEquals("***(5)", RealtimeFlowLog.maskSecret(" short "));
		assertEquals("123456...cdef(16)", RealtimeFlowLog.maskSecret("1234567890abcdef"));
		assertEquals("{length=0}", RealtimeFlowLog.sdpSummary(null));
		assertEquals("{length=0}", RealtimeFlowLog.sdpSummary(" "));
		assertTrue(RealtimeFlowLog.sdpSummary("v=0\r\na=1").contains("v=0\\r\\na=1"));
		assertTrue(RealtimeFlowLog.sdpSummary("x".repeat(241)).contains("..."));
		assertEquals("{length=0}", RealtimeFlowLog.textSummary(null));
		assertEquals("{length=0}", RealtimeFlowLog.textSummary(" "));
		assertTrue(RealtimeFlowLog.textSummary("hello\nworld").contains("hello\\nworld"));
		assertTrue(RealtimeFlowLog.textSummary("x".repeat(241)).contains("..."));
		assertEquals("", RealtimeFlowLog.uriWithoutQuery(null));
		assertEquals("", RealtimeFlowLog.uriWithoutQuery(" "));
		assertEquals("https://example.test/path", RealtimeFlowLog.uriWithoutQuery("https://example.test/path?token=secret#fragment"));
	}
}
