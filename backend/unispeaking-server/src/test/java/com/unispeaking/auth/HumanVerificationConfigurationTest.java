package com.unispeaking.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;

class HumanVerificationConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(GatewayConfiguration.class);

    @Test
    void developmentVerifierIsNotEnabledWhenProviderIsOmitted() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(DevelopmentHumanVerificationGateway.class));
    }

    @Import(DevelopmentHumanVerificationGateway.class)
    static class GatewayConfiguration {
    }
}
