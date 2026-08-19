package com.unispeaking.telemetry;

@FunctionalInterface
public interface ClientTelemetrySink {

	void write(ClientTelemetryRecord record);
}
