package com.unispeaking.service.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.unispeaking.domain.dto.profile.UpdateUserPreferenceRequest;
import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.domain.vo.profile.PreferredVoice;
import com.unispeaking.infrastructure.persistence.repository.user.UserProfileRepository;
import com.unispeaking.service.profile.impl.ProfileServiceImpl;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class ProfileServiceImplTest {

	@Test
	void updatesLongTermProfileOnlyFromExplicitPreferenceInput() {
		var service = new ProfileServiceImpl(new TestUserProfileRepository());
		String longTermProfile = """
				兴趣与背景：喜欢科技、电影和旅行，从事软件产品相关工作，熟悉会议和演示场景。
				个人信息：昵称 Sunny；希望使用中性称谓；不希望讨论具体公司和客户。
				""";

		var updated = service.updatePreference(
				"user-1",
				new UpdateUserPreferenceRequest(null, null, null, longTermProfile));

		assertEquals(longTermProfile.strip(), updated.memoryText());

		var voiceOnlyUpdate = service.updatePreference(
				"user-1",
				new UpdateUserPreferenceRequest(PreferredVoice.Harvey, null, null, null));

		assertEquals("Harvey", voiceOnlyUpdate.preferredVoice());
		assertEquals(longTermProfile.strip(), voiceOnlyUpdate.memoryText());
	}

	@Test
	void allowsUserToClearLongTermProfileWithoutCreatingConversationSummary() {
		var service = new ProfileServiceImpl(new TestUserProfileRepository());
		service.updatePreference(
				"user-2",
				new UpdateUserPreferenceRequest(
						null,
						null,
						null,
						"兴趣与背景：喜欢健身。"));

		var cleared = service.updatePreference(
				"user-2",
				new UpdateUserPreferenceRequest(null, null, null, "  "));

		assertEquals("", cleared.memoryText());
	}

	private static final class TestUserProfileRepository
			implements UserProfileRepository {

		private final Map<String, UserProfile> profiles =
				new ConcurrentHashMap<>();

		@Override
		public Optional<UserProfile> findByUserId(String userId) {
			return Optional.ofNullable(profiles.get(userId));
		}

		@Override
		public UserProfile save(UserProfile profile) {
			profiles.put(profile.userId(), profile);
			return profile;
		}
	}
}
