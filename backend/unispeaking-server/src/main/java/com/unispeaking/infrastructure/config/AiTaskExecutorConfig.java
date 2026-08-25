package com.unispeaking.infrastructure.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AiTaskExecutorConfig {

	@Bean(name = "customSceneGenerationExecutor")
	public Executor customSceneGenerationExecutor() {
		return executor("custom-scene-generation-", 2, 4, 30);
	}

	@Bean(name = "ieltsEvaluationExecutor")
	public Executor ieltsEvaluationExecutor() {
		return executor("ielts-evaluation-", 2, 4, 30);
	}

	@Bean(name = "ieltsPartEvaluationExecutor")
	public Executor ieltsPartEvaluationExecutor() {
		return executor("ielts-part-evaluation-", 3, 6, 30);
	}

	@Bean(name = "turnEvaluationExecutor")
	public Executor turnEvaluationExecutor() {
		return executor("turn-evaluation-", 4, 8, 60);
	}

	private Executor executor(
			String threadNamePrefix,
			int corePoolSize,
			int maxPoolSize,
			int queueCapacity) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix(threadNamePrefix);
		executor.setCorePoolSize(corePoolSize);
		executor.setMaxPoolSize(maxPoolSize);
		executor.setQueueCapacity(queueCapacity);
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(15);
		executor.initialize();
		return executor;
	}
}
