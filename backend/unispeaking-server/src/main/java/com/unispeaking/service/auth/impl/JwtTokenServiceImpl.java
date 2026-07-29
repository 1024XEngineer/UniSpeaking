package com.unispeaking.service.auth.impl;

import com.unispeaking.domain.po.user.UserAccount;
import com.unispeaking.domain.vo.auth.IssuedJwt;
import com.unispeaking.infrastructure.config.JwtProperties;
import com.unispeaking.service.auth.JwtTokenService;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenServiceImpl implements JwtTokenService {

	private final JwtEncoder jwtEncoder;
	private final JwtProperties properties;

	public JwtTokenServiceImpl(JwtEncoder jwtEncoder, JwtProperties properties) {
		this.jwtEncoder = jwtEncoder;
		this.properties = properties;
	}

	@Override
	public IssuedJwt issue(UserAccount user) {
		Instant issuedAt = Instant.now();
		Instant expiresAt = issuedAt.plus(properties.getAccessTokenTtl());
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(properties.getIssuer())
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.subject(user.id().toString())
				.claim("username", user.username())
				.claim("role", user.role().name())
				.claim("auth_version", user.authVersion())
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedJwt(token, expiresAt);
	}
}
