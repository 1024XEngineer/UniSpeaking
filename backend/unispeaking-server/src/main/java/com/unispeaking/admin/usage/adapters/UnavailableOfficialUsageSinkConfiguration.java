package com.unispeaking.admin.usage.adapters;

import com.unispeaking.admin.usage.ports.OfficialUsageSink;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnMissingBean(OfficialUsageSink.class)
public class UnavailableOfficialUsageSinkConfiguration {
    @Bean
    UnavailableOfficialUsageSink unavailableOfficialUsageSink() {
        return new UnavailableOfficialUsageSink();
    }
}
