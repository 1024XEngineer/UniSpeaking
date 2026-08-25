package com.unispeaking.domain.po.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UserProfileCoverageTest {
    @Test
    void defaultsAndTrimsOptionalProfileFieldsAndPreservesOrReplacesPreferences() {
        UserProfile defaults = new UserProfile("user", "B", "voice", null, "zh", null, null);
        assertEquals("NATURAL", defaults.aiSpeechSpeed());
        assertEquals("", defaults.memoryText());
        assertEquals("{}", defaults.preferencesJson());
        UserProfile blank = new UserProfile("user", "B", "voice", " ", "zh", " memory ", " prefs ");
        assertEquals("NATURAL", blank.aiSpeechSpeed());
        assertEquals("memory", blank.memoryText());
        assertEquals("prefs", blank.preferencesJson());

        UserProfile preserved = blank.withPreferences(null, null, null, null);
        assertEquals(blank.level(), preserved.level());
        assertEquals(blank.voiceId(), preserved.voiceId());
        assertEquals(blank.aiSpeechSpeed(), preserved.aiSpeechSpeed());
        assertEquals(blank.memoryText(), preserved.memoryText());
        UserProfile replaced = blank.withPreferences("new-voice", "SLOWER", "C", "new memory");
        assertEquals("C", replaced.level());
        assertEquals("new-voice", replaced.voiceId());
    }
}
