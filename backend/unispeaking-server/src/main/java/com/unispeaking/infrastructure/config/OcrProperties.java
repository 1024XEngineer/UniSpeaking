package com.unispeaking.infrastructure.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ocr.paddle")
public class OcrProperties {

	private boolean enabled = true;
	private String pythonExecutable = "/opt/ocr-venv/bin/python";
	private String runnerPath = "/app/ocr/paddle_ocr_runner.py";
	private String modelDirectory = "/app/ocr/models";
	private String tempDirectory = "";
	private Duration timeout = Duration.ofSeconds(120);

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getPythonExecutable() {
		return pythonExecutable;
	}

	public void setPythonExecutable(String value) {
		pythonExecutable = trim(value);
	}

	public Path runnerPath() {
		return Path.of(runnerPath);
	}

	public String getRunnerPath() {
		return runnerPath;
	}

	public void setRunnerPath(String value) {
		runnerPath = trim(value);
	}

	public Path modelDirectory() {
		return Path.of(modelDirectory);
	}

	public String getModelDirectory() {
		return modelDirectory;
	}

	public void setModelDirectory(String value) {
		modelDirectory = trim(value);
	}

	public Path tempDirectory() {
		return tempDirectory.isBlank() ? null : Path.of(tempDirectory);
	}

	public String getTempDirectory() {
		return tempDirectory;
	}

	public void setTempDirectory(String value) {
		tempDirectory = trim(value);
	}

	public Duration getTimeout() {
		return timeout;
	}

	public void setTimeout(Duration value) {
		timeout = value;
	}

	public boolean configured() {
		return enabled
				&& !pythonExecutable.isBlank()
				&& !runnerPath.isBlank()
				&& !modelDirectory.isBlank()
				&& timeout != null
				&& !timeout.isZero()
				&& !timeout.isNegative();
	}

	private static String trim(String value) {
		return value == null ? "" : value.trim();
	}
}
