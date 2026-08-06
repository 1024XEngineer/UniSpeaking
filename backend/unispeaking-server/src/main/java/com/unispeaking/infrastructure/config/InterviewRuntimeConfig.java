package com.unispeaking.infrastructure.config;

import com.unispeaking.component.session.InterviewRuntimePolicy;
import java.time.Clock;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class InterviewRuntimeConfig {

	@Bean(name = "interviewTaskExecutor")
	public Executor interviewTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("interview-task-");
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(8);
		executor.setQueueCapacity(100);
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(30);
		executor.initialize();
		return executor;
	}

	@Bean(name = "interviewClock")
	public Clock interviewClock() {
		return Clock.systemUTC();
	}

	@Bean
	public InterviewRuntimePolicy interviewRuntimePolicy() {
		return InterviewRuntimePolicy.defaults();
	}

	@Bean(name = "interviewWatchdogScheduler", destroyMethod = "shutdown")
	public ScheduledExecutorService interviewWatchdogScheduler() {
		return Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "interview-watchdog-1");
			thread.setDaemon(true);
			return thread;
		});
	}
}
