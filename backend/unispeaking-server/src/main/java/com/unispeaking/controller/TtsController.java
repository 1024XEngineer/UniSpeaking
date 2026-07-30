package com.unispeaking.controller;

import com.unispeaking.domain.dto.request.TtsRequest;
import com.unispeaking.service.tts.TtsService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tts")
public class TtsController {

	private final TtsService ttsService;

	public TtsController(TtsService ttsService) {
		this.ttsService = ttsService;
	}

	@PostMapping(value = "/synthesize", produces = "audio/wav")
	public ResponseEntity<byte[]> synthesize(@Valid @RequestBody TtsRequest request) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.contentType(MediaType.parseMediaType("audio/wav"))
				.body(ttsService.synthesize(request.text(), request.model()));
	}
}
