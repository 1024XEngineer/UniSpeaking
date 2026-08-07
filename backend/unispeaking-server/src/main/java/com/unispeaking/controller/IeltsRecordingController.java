package com.unispeaking.controller;

import com.unispeaking.service.recording.IeltsRecordingService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ielts/recordings")
public class IeltsRecordingController {

	private final IeltsRecordingService recordingService;

	public IeltsRecordingController(IeltsRecordingService recordingService) {
		this.recordingService = recordingService;
	}

	@GetMapping(value = "/{sessionId}/{fileName:.+}", produces = "audio/wav")
	public ResponseEntity<Resource> getRecording(
			@PathVariable String sessionId,
			@PathVariable String fileName) {
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("audio/wav"))
				.cacheControl(CacheControl.noStore().cachePrivate())
				.body(recordingService.loadOwned(sessionId, fileName));
	}
}
