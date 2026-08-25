package com.unispeaking.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class OcrPropertiesTest {
    @Test
    void exposesTrimmedPathsAndOptionalTempDirectory() {
        OcrProperties properties = new OcrProperties();
        assertTrue(properties.configured());
        assertNull(properties.tempDirectory());
        properties.setPythonExecutable(" python ");
        properties.setRunnerPath(" /runner.py ");
        properties.setModelDirectory(" /models ");
        properties.setTempDirectory(" /tmp/ocr ");
        properties.setTimeout(Duration.ofSeconds(1));
        assertEquals("python", properties.getPythonExecutable());
        assertEquals(Path.of("/runner.py"), properties.runnerPath());
        assertEquals(Path.of("/models"), properties.modelDirectory());
        assertEquals(Path.of("/tmp/ocr"), properties.tempDirectory());
        assertEquals(Duration.ofSeconds(1), properties.getTimeout());
    }

    @Test
    void everyRequiredConfigurationConditionCanDisableOcr() {
        OcrProperties properties = new OcrProperties();
        properties.setEnabled(false);
        assertFalse(properties.configured());
        properties.setEnabled(true);
        properties.setPythonExecutable(null);
        assertFalse(properties.configured());
        properties.setPythonExecutable("python"); properties.setRunnerPath(null);
        assertFalse(properties.configured());
        properties.setRunnerPath("runner"); properties.setModelDirectory(" ");
        assertFalse(properties.configured());
        properties.setModelDirectory("models"); properties.setTimeout(null);
        assertFalse(properties.configured());
        properties.setTimeout(Duration.ZERO);
        assertFalse(properties.configured());
        properties.setTimeout(Duration.ofSeconds(-1));
        assertFalse(properties.configured());
    }
}
