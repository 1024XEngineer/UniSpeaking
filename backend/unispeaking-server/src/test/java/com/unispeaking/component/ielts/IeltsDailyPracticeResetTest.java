package com.unispeaking.component.ielts;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.unispeaking.infrastructure.persistence.repository.scene.IeltsPracticeRepository;
import org.junit.jupiter.api.Test;

class IeltsDailyPracticeResetTest {

	@Test
	void delegatesMidnightResetToRepository() {
		IeltsPracticeRepository repository =
				mock(IeltsPracticeRepository.class);

		new IeltsDailyPracticeReset(repository).resetCompletedCounts();

		verify(repository).resetCompletedCounts();
	}
}
