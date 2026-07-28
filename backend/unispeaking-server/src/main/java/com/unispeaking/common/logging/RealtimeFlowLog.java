package com.unispeaking.common.logging;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RealtimeFlowLog {

	private static final int PREVIEW_LIMIT = 240;
	private static final Logger LOG = LoggerFactory.getLogger("com.unispeaking.realtimeflow");

	private RealtimeFlowLog() {
	}

	public static void info(String message, Object... arguments) {
		LOG.info("[realtime-flow] " + message, arguments);
	}

	public static String maskSecret(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		String clean = value.trim();
		if (clean.length() <= 10) {
			return "***(" + clean.length() + ")";
		}
		return clean.substring(0, 6) + "..." + clean.substring(clean.length() - 4)
				+ "(" + clean.length() + ")";
	}

	public static String sdpSummary(String sdp) {
		if (sdp == null || sdp.isBlank()) {
			return "{length=0}";
		}
		String normalized = sdp.replace("\r", "\\r").replace("\n", "\\n");
		String preview = normalized.length() <= PREVIEW_LIMIT
				? normalized
				: normalized.substring(0, PREVIEW_LIMIT) + "...";
		return "{length=" + sdp.length()
				+ ", hash=" + Integer.toHexString(sdp.hashCode())
				+ ", preview=\"" + preview + "\"}";
	}

	public static String textSummary(String value) {
		if (value == null || value.isBlank()) {
			return "{length=0}";
		}
		String normalized = value.replace("\r", "\\r").replace("\n", "\\n");
		String preview = normalized.length() <= PREVIEW_LIMIT
				? normalized
				: normalized.substring(0, PREVIEW_LIMIT) + "...";
		return "{length=" + value.length() + ", preview=\"" + preview + "\"}";
	}

	public static String uriWithoutQuery(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		URI uri = URI.create(value);
		return URI.create(uri.getScheme() + "://" + uri.getAuthority() + uri.getPath()).toString();
	}
}
