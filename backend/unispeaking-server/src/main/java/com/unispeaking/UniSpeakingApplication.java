package com.unispeaking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class UniSpeakingApplication {

	public static void main(String[] args) {
		SpringApplication.run(UniSpeakingApplication.class, args);
	}
}
