package com.unispeaking.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.unispeaking.domain.dto.session.IceServerConfigurationResponse;
import com.unispeaking.service.session.RealtimeNetworkService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RealtimeNetworkControllerTest {

	private RealtimeNetworkService service;
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		service = mock(RealtimeNetworkService.class);
		mvc = MockMvcBuilders.standaloneSetup(new RealtimeNetworkController(service)).build();
	}

	@Test
	void returnsTemporaryRelayConfiguration() throws Exception {
		when(service.getIceConfiguration(true)).thenReturn(new IceServerConfigurationResponse(
				true,
				"relay",
				List.of(new IceServerConfigurationResponse.IceServer(
						List.of("turn:turn.example.cn:443?transport=udp"),
						"1893456000:opaque",
						"temporary-credential")),
				Instant.ofEpochSecond(1893456000)));

		mvc.perform(get("/api/realtime/ice-configuration").param("forceRelay", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.turnEnabled").value(true))
				.andExpect(jsonPath("$.data.iceTransportPolicy").value("relay"))
				.andExpect(jsonPath("$.data.iceServers[0].urls[0]")
						.value("turn:turn.example.cn:443?transport=udp"))
				.andExpect(jsonPath("$.data.iceServers[0].credential")
						.value("temporary-credential"));

		verify(service).getIceConfiguration(true);
	}
}
