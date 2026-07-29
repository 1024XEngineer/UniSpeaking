package com.unispeaking;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.unispeaking.infrastructure.persistence.mybatis.mapper")
public class UniSpeakingApplication {

	public static void main(String[] args) {
		SpringApplication.run(UniSpeakingApplication.class, args);
	}
}
