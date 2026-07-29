package com.unispeaking.service.auth;

import com.unispeaking.domain.po.user.UserAccount;
import com.unispeaking.domain.vo.auth.IssuedJwt;

public interface JwtTokenService {
	IssuedJwt issue(UserAccount user);
}
