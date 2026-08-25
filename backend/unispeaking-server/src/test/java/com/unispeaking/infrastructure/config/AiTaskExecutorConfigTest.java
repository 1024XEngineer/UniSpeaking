package com.unispeaking.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AiTaskExecutorConfigTest {

    @Test
    void createsNamedPoolsWithExpectedConcurrencyLimits() {
        AiTaskExecutorConfig config = new AiTaskExecutorConfig();

        assertPool(config.customSceneGenerationExecutor(), "custom-scene-generation-", 2, 4);
        assertPool(config.ieltsEvaluationExecutor(), "ielts-evaluation-", 2, 4);
        assertPool(config.ieltsPartEvaluationExecutor(), "ielts-part-evaluation-", 3, 6);
        assertPool(config.turnEvaluationExecutor(), "turn-evaluation-", 4, 8);
    }

    private void assertPool(Executor executor, String prefix, int core, int max) {
        ThreadPoolTaskExecutor pool = assertInstanceOf(ThreadPoolTaskExecutor.class, executor);
        assertEquals(prefix, pool.getThreadNamePrefix());
        assertEquals(core, pool.getCorePoolSize());
        assertEquals(max, pool.getMaxPoolSize());
        pool.shutdown();
    }
}
