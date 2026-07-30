package com.unispeaking.service.account;

import com.unispeaking.exception.BusinessException;
import com.unispeaking.infrastructure.config.QiniuAvatarProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AvatarUrlResolver {

	private final QiniuAvatarProperties properties;

	public AvatarUrlResolver(QiniuAvatarProperties properties) {
		this.properties = properties;
	}

	public String resolve(String objectKey) {
		if (objectKey == null || objectKey.isBlank()) {
			return null;
		}
		String domain = trimTrailingSlash(properties.getDomain());
		if (domain.isBlank()) {
			throw new BusinessException(
					"AVATAR_STORAGE_UNAVAILABLE",
					"头像访问域名尚未配置");
		}
		String encodedPath = Arrays.stream(objectKey.split("/", -1))
				.map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8)
						.replace("+", "%20"))
				.collect(Collectors.joining("/"));
		return domain + "/" + encodedPath;
	}

	private String trimTrailingSlash(String value) {
		String result = value == null ? "" : value.trim();
		while (result.endsWith("/")) {
			result = result.substring(0, result.length() - 1);
		}
		return result;
	}
}
