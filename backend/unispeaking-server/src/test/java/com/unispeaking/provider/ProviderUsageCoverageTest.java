package com.unispeaking.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class ProviderUsageCoverageTest {
    @Test
    void normalizesValuesAndExercisesEveryFactory() {
        ProviderUsage normalized = new ProviderUsage(-1, 2, -3, 4, -1, -2, " raw ");
        assertEquals(0, normalized.inputTokens());
        assertEquals(2, normalized.totalTokens());
        assertEquals("RAW", normalized.source());
        assertEquals("NONE", new ProviderUsage(0, 0, 0, 0, 0, 0, null).source());
        assertEquals("NONE", new ProviderUsage(0, 0, 0, 0, 0, 0, " ").source());

        assertEquals(0, ProviderUsage.estimatedText(null, "").totalTokens());
        assertEquals(1, ProviderUsage.estimatedText("😀", "a").inputCharacters());
		assertEquals(3, ProviderUsage.ttsInput("abc").inputCharacters());
        assertEquals(0.5, ProviderUsage.tts("text", wav(100, 50)).audioOutputSeconds());
        assertEquals(0.5, ProviderUsage.audioInput(wav(100, 50), "answer").audioInputSeconds());
        assertEquals(0.5, ProviderUsage.scoring("reference", wav(100, 50)).audioInputSeconds());
    }

    @Test
    void rejectsMalformedWavShapesAsZeroDuration() {
        assertEquals(0, ProviderUsage.wavSeconds(null));
        assertEquals(0, ProviderUsage.wavSeconds(new byte[10]));
        byte[] wrong = new byte[44];
        wrong[0] = 'R'; wrong[1] = 'I'; wrong[2] = 'F'; wrong[3] = 'X';
        assertEquals(0, ProviderUsage.wavSeconds(wrong));
        byte[] zeroRate = wav(0, 10);
        assertEquals(0, ProviderUsage.wavSeconds(zeroRate));
        assertEquals(0, ProviderUsage.wavSeconds(wavWithoutData()));
    }

    private byte[] wav(int byteRate, int dataSize) {
        byte[] bytes = new byte[44 + dataSize];
        System.arraycopy("RIFF".getBytes(), 0, bytes, 0, 4);
        System.arraycopy("WAVEfmt ".getBytes(), 0, bytes, 8, 8);
        System.arraycopy("data".getBytes(), 0, bytes, 36, 4);
		ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
				.putInt(16, 16).putInt(28, byteRate).putInt(40, dataSize);
        return bytes;
    }

    private byte[] wavWithoutData() {
        byte[] bytes = wav(100, 0);
        System.arraycopy("JUNK".getBytes(), 0, bytes, 36, 4);
        return bytes;
    }
}
