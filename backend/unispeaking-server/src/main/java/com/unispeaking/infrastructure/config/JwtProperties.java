package com.unispeaking.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("auth.jwt")
public class JwtProperties {

	private String issuer = "unispeaking";
	private String secret = "";
	private Duration accessTokenTtl = Duration.ofMinutes(30);
	private Duration refreshIdleTtl = Duration.ofDays(7);
	private Duration refreshAbsoluteTtl = Duration.ofDays(90);

	public String getIssuer() {
		return issuer;
	}

	public void setIssuer(String issuer) {
		this.issuer = issuer;
	}

	public String getSecret() {
		return secret;
	}

	public void setSecret(String secret) {
		this.secret = secret;
	}

	public Duration getAccessTokenTtl() {
		return accessTokenTtl;
	}

	public void setAccessTokenTtl(Duration accessTokenTtl) {
		this.accessTokenTtl = accessTokenTtl;
	}

	public Duration getRefreshIdleTtl() {
		return refreshIdleTtl;
	}

	public void setRefreshIdleTtl(Duration refreshIdleTtl) {
		this.refreshIdleTtl = refreshIdleTtl;
	}

	public Duration getRefreshAbsoluteTtl() {
		return refreshAbsoluteTtl;
	}

	public void setRefreshAbsoluteTtl(Duration refreshAbsoluteTtl) {
		this.refreshAbsoluteTtl = refreshAbsoluteTtl;
	}
}
