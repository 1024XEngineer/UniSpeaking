package com.unispeaking.infrastructure.persistence.repository.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.po.profile.UserProfile;
import com.unispeaking.infrastructure.persistence.entity.user.UserPreferenceEntity;
import com.unispeaking.infrastructure.persistence.mapper.user.UserPreferenceMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MybatisUserProfileRepositoryTest {

    @Test
    void returnsEmptyWhenPreferenceDoesNotExist() {
        UUID userId = UUID.randomUUID();
        UserPreferenceMapper mapper = mock(UserPreferenceMapper.class);
        when(mapper.selectById(userId)).thenReturn(null);

        assertTrue(new MybatisUserProfileRepository(mapper)
                .findByUserId(userId.toString())
                .isEmpty());
    }

    @Test
    void mapsStoredPreferenceFieldsToDomain() {
        UUID userId = UUID.randomUUID();
        UserPreferenceEntity entity = new UserPreferenceEntity();
        entity.setUserId(userId);
        entity.setCefrLevel("B2");
        entity.setPreferredVoice("clara");
        entity.setPreferredAiSpeechSpeed("SLOW");
        entity.setMemoryText("  remember this  ");
        entity.setPreferences("{\"translation_enabled\":true}");
        UserPreferenceMapper mapper = mock(UserPreferenceMapper.class);
        when(mapper.selectById(userId)).thenReturn(entity);

        UserProfile profile = new MybatisUserProfileRepository(mapper)
                .findByUserId(userId.toString())
                .orElseThrow();

        assertEquals(userId.toString(), profile.userId());
        assertEquals("B2", profile.level());
        assertEquals("clara", profile.voiceId());
        assertEquals("SLOW", profile.aiSpeechSpeed());
        assertEquals("zh-CN", profile.nativeLanguage());
        assertEquals("remember this", profile.memoryText());
        assertEquals("{\"translation_enabled\":true}", profile.preferencesJson());
    }

    @Test
    void insertsNewPreferenceAndReturnsTheSameProfile() {
        UUID userId = UUID.randomUUID();
        UserPreferenceMapper mapper = mock(UserPreferenceMapper.class);
        UserProfile profile = new UserProfile(
                userId.toString(), "C1", "james", "FAST", "zh-CN", "memory", "{}");
        when(mapper.selectById(userId)).thenReturn(null);

        UserProfile saved = new MybatisUserProfileRepository(mapper).save(profile);

        assertSame(profile, saved);
        ArgumentCaptor<UserPreferenceEntity> captor =
                ArgumentCaptor.forClass(UserPreferenceEntity.class);
        verify(mapper).insert(captor.capture());
        UserPreferenceEntity inserted = captor.getValue();
        assertEquals(userId, inserted.getUserId());
        assertEquals("C1", inserted.getCefrLevel());
        assertEquals("james", inserted.getPreferredVoice());
        assertEquals("FAST", inserted.getPreferredAiSpeechSpeed());
        assertEquals("memory", inserted.getMemoryText());
        assertTrue(inserted.getCreatedAt() != null);
        assertTrue(inserted.getUpdatedAt() != null);
    }

    @Test
    void updatesExistingPreferenceInPlace() {
        UUID userId = UUID.randomUUID();
        UserPreferenceMapper mapper = mock(UserPreferenceMapper.class);
        UserPreferenceEntity entity = new UserPreferenceEntity();
        entity.setUserId(userId);
        entity.setCefrLevel("A2");
        when(mapper.selectById(userId)).thenReturn(entity);
        UserProfile profile = new UserProfile(
                userId.toString(), "B1", "emily", "NATURAL", "zh-CN", "updated", "{}");

        UserProfile saved = new MybatisUserProfileRepository(mapper).save(profile);

        assertSame(profile, saved);
        verify(mapper).updateById(entity);
        assertEquals("B1", entity.getCefrLevel());
        assertEquals("emily", entity.getPreferredVoice());
        assertEquals("NATURAL", entity.getPreferredAiSpeechSpeed());
        assertEquals("updated", entity.getMemoryText());
        assertTrue(entity.getUpdatedAt() != null);
    }
}
