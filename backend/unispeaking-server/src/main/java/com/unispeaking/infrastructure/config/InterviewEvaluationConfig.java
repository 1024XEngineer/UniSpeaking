package com.unispeaking.infrastructure.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Interview 报告任务独立 executor：有界线程池 + AbortPolicy（非 CallerRuns）。
 * 池满时拒绝新任务并交由僵尸清扫重派，绝不静默退化为同步执行（区别于
 * {@code scenePersistenceExecutor} 的 CallerRuns）。
 */
@Configuration
public class InterviewEvaluationConfig {

	@Bean(name = "interviewEvaluationExecutor")
	public Executor interviewEvaluationExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("interview-eval-");
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(50);
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(10);
		executor.initialize();
		return executor;
	}
}
