package com.unispeaking.component.ielts;

import com.unispeaking.infrastructure.persistence.repository.scene.IeltsPracticeRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public final class IeltsDailyPracticeReset {

	private final IeltsPracticeRepository repository;

	public IeltsDailyPracticeReset(IeltsPracticeRepository repository) {
		this.repository = repository;
	}

	@Scheduled(
			cron = "${ielts.daily-reset-cron:0 0 0 * * *}",
			zone = "${profile.time-zone:Asia/Shanghai}")
	public void resetCompletedCounts() {
		repository.resetCompletedCounts();
	}
}
