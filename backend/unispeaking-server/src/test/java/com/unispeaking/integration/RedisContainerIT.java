package com.unispeaking.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class RedisContainerIT {

	private static final int REDIS_PORT = 6379;

	@Container
	static final GenericContainer<?> REDIS =
			new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
					.withExposedPorts(REDIS_PORT)
					.waitingFor(Wait.forListeningPort());

	@Test
	void writesReadsAndExpiresValue() throws InterruptedException {
		RedisURI uri = RedisURI.builder()
				.withHost(REDIS.getHost())
				.withPort(REDIS.getMappedPort(REDIS_PORT))
				.withTimeout(Duration.ofSeconds(5))
				.build();
		RedisClient client = RedisClient.create(uri);
		try (StatefulRedisConnection<String, String> connection =
				client.connect()) {
			RedisCommands<String, String> commands = connection.sync();
			String key = "ci:ttl";

			commands.psetex(key, 800, "ready");

			assertEquals("ready", commands.get(key));
			long remainingTtl = commands.pttl(key);
			assertTrue(remainingTtl > 0 && remainingTtl <= 800);
			awaitExpiration(commands, key);
			assertNull(commands.get(key));
		}
		finally {
			client.shutdown();
		}
	}

	private void awaitExpiration(
			RedisCommands<String, String> commands,
			String key) throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (commands.get(key) != null && System.nanoTime() < deadline) {
			Thread.sleep(50);
		}
	}
}
