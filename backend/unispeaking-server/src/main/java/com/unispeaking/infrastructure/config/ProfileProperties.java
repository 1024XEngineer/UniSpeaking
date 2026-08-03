package com.unispeaking.infrastructure.config;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("profile")
public class ProfileProperties {
	private String timeZone = "Asia/Shanghai";

	public String getTimeZone() {
		return timeZone;
	}

	public void setTimeZone(String timeZone) {
		this.timeZone = timeZone;
	}

	public ZoneId zoneId() {
		return ZoneId.of(timeZone);
	}
}
