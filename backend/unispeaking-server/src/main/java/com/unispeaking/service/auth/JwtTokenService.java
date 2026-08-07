package com.unispeaking.service.auth;

import com.unispeaking.domain.po.auth.UserAccount;
import com.unispeaking.domain.vo.auth.IssuedJwt;

public interface JwtTokenService {
	/** Issues an access token for the supplied user account. */
	IssuedJwt issue(UserAccount user);
}
