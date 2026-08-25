package com.unispeaking.common.audio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class EncodedAudioTest {
    @Test
    void validatesFieldsTrimsMediaTypeAndDefensivelyCopiesContent() {
        assertThrows(NullPointerException.class,
				() -> new EncodedAudio(null, "audio/wav", Duration.ofSeconds(1)));
        assertThrows(NullPointerException.class,
				() -> new EncodedAudio(new byte[] {1}, null, Duration.ofSeconds(1)));
        assertThrows(NullPointerException.class,
                () -> new EncodedAudio(new byte[] {1}, "audio/wav", null));
        assertThrows(IllegalArgumentException.class,
				() -> new EncodedAudio(new byte[0], "audio/wav", Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
				() -> new EncodedAudio(new byte[] {1}, " ", Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new EncodedAudio(new byte[] {1}, "audio/wav", Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new EncodedAudio(new byte[] {1}, "audio/wav", Duration.ofSeconds(-1)));
        byte[] source = {1, 2};
		EncodedAudio audio = new EncodedAudio(source, " audio/wav ", Duration.ofSeconds(1));
        source[0] = 9;
        byte[] returned = audio.content();
        returned[1] = 9;
        assertArrayEquals(new byte[] {1, 2}, audio.content());
        assertEquals("audio/wav", audio.mediaType());
    }
}
