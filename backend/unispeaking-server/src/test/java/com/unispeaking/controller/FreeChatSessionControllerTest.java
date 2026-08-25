package com.unispeaking.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.unispeaking.domain.dto.scene.TranslateTextRequest;
import com.unispeaking.domain.dto.session.StartFreeChatRequest;
import com.unispeaking.service.scene.FreeChatSceneService;
import com.unispeaking.service.session.FreeChatSessionService;
import org.junit.jupiter.api.Test;

class FreeChatSessionControllerTest {
    private final FreeChatSessionService sessions = mock(FreeChatSessionService.class);
    private final FreeChatSceneService scenes = mock(FreeChatSceneService.class);
    private final FreeChatSessionController controller = new FreeChatSessionController(sessions, scenes);

    @Test
    void delegatesStartEndAndTranslation() {
        StartFreeChatRequest request = new StartFreeChatRequest("sdp", null, "model", "voice", true);

        assertTrue(controller.start(request).success());
        assertTrue(controller.end("session-1").success());
        assertTrue(controller.translate("session-1", new TranslateTextRequest("hello")).success());
        verify(sessions).startSession(request);
        verify(sessions).endSession("session-1");
        verify(scenes).translate("hello");
    }
}
