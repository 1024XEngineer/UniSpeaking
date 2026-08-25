package com.unispeaking.common.audio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unispeaking.common.exception.audio.AudioException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PcmWavParserCoverageTest {

    @Test
    void parsesPcmWithUnknownPaddedAndExtendedFormatChunks() {
        byte[] pcm = {1, 2, 3, 4};
        PcmWavData standard = PcmWavParser.parse(riff(
                chunk("JUNK", new byte[] {9}),
                chunk("fmt ", format(1, 1, 16_000, 32_000, 2, 16)),
                chunk("data", pcm)));
        PcmWavData extended = PcmWavParser.parse(riff(
                chunk("fmt ", extendedFormat()),
                chunk("data", pcm)));

        assertEquals(16_000, standard.sampleRate());
        assertEquals(1, standard.channels());
        assertArrayEquals(pcm, standard.pcm());
        assertArrayEquals(pcm, extended.pcm());
    }

    @Test
    void rejectsMissingOrCorruptRiffEnvelope() {
        assertInvalid(null);
        assertInvalid(new byte[0]);
        assertInvalid("NOPE".getBytes(StandardCharsets.US_ASCII));
        assertInvalid("RIFF".getBytes(StandardCharsets.US_ASCII));

        byte[] wrongWave = validMono();
        wrongWave[8] = 'N';
        assertInvalid(wrongWave);

        byte[] wrongDeclaredSize = validMono();
        putInt(wrongDeclaredSize, 4, wrongDeclaredSize.length);
        assertInvalid(wrongDeclaredSize);

        byte[] trailingHeader = new byte[13];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, trailingHeader, 0, 4);
        putInt(trailingHeader, 4, 5);
        System.arraycopy("WAVE".getBytes(StandardCharsets.US_ASCII), 0, trailingHeader, 8, 4);
        assertInvalid(trailingHeader);
    }

    @Test
    void rejectsInvalidChunkLayoutAndDuplicates() {
        byte[] oversized = validMono();
        putInt(oversized, 16, Integer.MAX_VALUE);
        assertInvalid(oversized);

        byte[] fmt = chunk("fmt ", format(1, 1, 16_000, 32_000, 2, 16));
        byte[] data = chunk("data", new byte[] {1, 2});
        assertInvalid(riff(fmt, fmt, data));
        assertInvalid(riff(fmt, data, data));
        assertInvalid(riff(data));
        assertInvalid(riff(fmt));
        assertInvalid(riff(fmt, chunk("data", new byte[0])));
        assertInvalid(riff(fmt, chunk("data", new byte[] {1})));
        assertInvalid(riff(
                chunk("fmt ", format(1, 2, 16_000, 64_000, 4, 16)),
                chunk("data", new byte[] {1, 2})));
    }

    @Test
    void rejectsInvalidFormatShapesAndValues() {
        byte[] data = chunk("data", new byte[] {1, 2, 3, 4});
        assertInvalid(riff(chunk("fmt ", new byte[15]), data));
        assertInvalid(riff(chunk("fmt ", new byte[17]), data));

        byte[] badExtension = new byte[18];
        putShort(badExtension, 16, 1);
        assertInvalid(riff(chunk("fmt ", badExtension), data));

        assertInvalidFormat(format(3, 1, 16_000, 32_000, 2, 16));
        assertInvalidFormat(format(1, 3, 16_000, 96_000, 6, 16));
        assertInvalidFormat(format(1, 1, 12_345, 24_690, 2, 16));
        assertInvalidFormat(format(1, 1, 16_000, 16_000, 2, 8));
        assertInvalidFormat(format(1, 1, 16_000, 32_000, 4, 16));
        assertInvalidFormat(format(1, 1, 16_000, 1, 2, 16));
    }

    private void assertInvalidFormat(byte[] format) {
        assertInvalid(riff(chunk("fmt ", format), chunk("data", new byte[] {1, 2, 3, 4})));
    }

    private void assertInvalid(byte[] wav) {
        assertThrows(AudioException.class, () -> PcmWavParser.parse(wav));
    }

    private byte[] validMono() {
        return riff(
                chunk("fmt ", format(1, 1, 16_000, 32_000, 2, 16)),
                chunk("data", new byte[] {1, 2, 3, 4}));
    }

    private byte[] extendedFormat() {
        byte[] bytes = new byte[18];
        byte[] standard = format(1, 1, 16_000, 32_000, 2, 16);
        System.arraycopy(standard, 0, bytes, 0, standard.length);
        putShort(bytes, 16, 0);
        return bytes;
    }

    private byte[] format(int audioFormat, int channels, int sampleRate,
            int byteRate, int blockAlign, int bitsPerSample) {
        return ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) audioFormat)
                .putShort((short) channels)
                .putInt(sampleRate)
                .putInt(byteRate)
                .putShort((short) blockAlign)
                .putShort((short) bitsPerSample)
                .array();
    }

    private byte[] chunk(String id, byte[] data) {
        ByteBuffer buffer = ByteBuffer.allocate(8 + data.length + (data.length & 1))
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(id.getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(data.length);
        buffer.put(data);
        return buffer.array();
    }

    private byte[] riff(byte[]... chunks) {
        int chunkBytes = 0;
        for (byte[] chunk : chunks) chunkBytes += chunk.length;
        ByteBuffer buffer = ByteBuffer.allocate(12 + chunkBytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII));
        buffer.putInt(4 + chunkBytes);
        buffer.put("WAVE".getBytes(StandardCharsets.US_ASCII));
        for (byte[] chunk : chunks) buffer.put(chunk);
        return buffer.array();
    }

    private void putInt(byte[] bytes, int offset, int value) {
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, value);
    }

    private void putShort(byte[] bytes, int offset, int value) {
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putShort(offset, (short) value);
    }
}
