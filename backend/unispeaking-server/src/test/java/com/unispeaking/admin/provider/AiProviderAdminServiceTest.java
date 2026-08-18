package com.unispeaking.admin.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.unispeaking.domain.vo.provider.AiCapability;
import com.unispeaking.domain.vo.provider.AiModelDefinition;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.provider.config.AiProviderCredentialStore;
import com.unispeaking.provider.config.JdbcAiConfigurationStore;
import java.util.List;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AiProviderAdminServiceTest {

	@Test
	void replacesDefaultRouteUsingModelPriorities() {
		JdbcTemplate jdbc = database();
		JdbcAiConfigurationStore store = new JdbcAiConfigurationStore(jdbc);
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		when(registry.deployedModels()).thenReturn(List.of(
				new AiModelDefinition("primary", "vendor", AiCapability.LLM, true),
				new AiModelDefinition("fallback", "vendor", AiCapability.LLM, false)));
		var service = new AiProviderAdminService(
				jdbc, store, registry, mock(AiProviderCredentialStore.class));

		assertThat(service.configuration().routes().stream()
				.filter(route -> route.capability() == AiCapability.LLM)
				.findFirst().orElseThrow().modelIds())
				.containsExactly("primary", "fallback");

		service.replaceRoute(AiCapability.LLM,
				new AiProviderAdminService.UpdateRouteRequest("default", List.of("fallback", "primary")));

		assertThat(jdbc.queryForList(
				"select model_id from ai_models where route_priority is not null order by route_priority",
				String.class)).containsExactly("fallback", "primary");
		assertThat(service.configuration().routes().stream()
				.filter(route -> route.capability() == AiCapability.LLM)
				.findFirst().orElseThrow().modelIds())
				.containsExactly("fallback", "primary");
	}

	private static JdbcTemplate database() {
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setURL("jdbc:h2:mem:ai-provider-admin;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		jdbc.execute("""
				create table ai_providers (
				    provider_id varchar(64) primary key,
				    display_name varchar(128) not null,
				    adapter_type varchar(64) not null,
				    base_url varchar(1000),
				    enabled boolean not null,
				    connect_timeout_ms integer not null,
				    read_timeout_ms integer not null,
				    config_version bigint not null,
				    secret_ciphertext varchar(10000),
				    secret_fingerprint varchar(32),
				    updated_at timestamp with time zone not null
				)
				""");
		jdbc.execute("""
				create table ai_models (
				    model_id varchar(128) primary key,
				    provider_id varchar(64) not null,
				    display_name varchar(128) not null,
				    capability varchar(32) not null,
				    enabled boolean not null,
				    route_priority integer,
				    billing_unit varchar(32) not null,
				    input_price_per_million decimal(20,8) not null,
				    output_price_per_million decimal(20,8) not null,
				    character_price_per_million decimal(20,8) not null,
				    audio_input_price_per_minute decimal(20,8) not null,
				    audio_output_price_per_minute decimal(20,8) not null,
				    request_price_per_call decimal(20,8) not null,
				    price_currency varchar(8) not null,
				    updated_at timestamp with time zone not null,
				    unique (capability, route_priority)
				)
				""");
		jdbc.update("""
				insert into ai_providers values
				('vendor', 'Vendor', 'vendor', null, true, 10000, 60000, 1, null, null, current_timestamp)
				""");
		jdbc.update("""
				insert into ai_models values
				('primary', 'vendor', 'Primary', 'LLM', true, 10, 'TOKENS', 0, 0, 0, 0, 0, 0, 'CNY', current_timestamp),
				('fallback', 'vendor', 'Fallback', 'LLM', true, 20, 'TOKENS', 0, 0, 0, 0, 0, 0, 'CNY', current_timestamp)
				""");
		return jdbc;
	}
}
