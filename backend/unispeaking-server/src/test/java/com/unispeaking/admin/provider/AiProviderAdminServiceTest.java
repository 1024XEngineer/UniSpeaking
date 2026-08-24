package com.unispeaking.admin.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unispeaking.common.exception.BusinessException;
import com.unispeaking.domain.vo.provider.AiCapability;
import com.unispeaking.domain.vo.provider.AiModelDefinition;
import com.unispeaking.provider.AiProviderRegistry;
import com.unispeaking.provider.config.AiProviderCredentialStore;
import com.unispeaking.provider.config.JdbcAiConfigurationStore;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

class AiProviderAdminServiceTest {

	private static final AiProviderCredentialStore.CredentialStatus CREDENTIAL_STATUS =
			new AiProviderCredentialStore.CredentialStatus(true, "fingerprint", true, List.of());

	@Test
	void updatesProviderAndModelEnabledStatesImmediately() {
		JdbcTemplate jdbc = database();
		JdbcAiConfigurationStore store = new JdbcAiConfigurationStore(jdbc);
		var service = new AiProviderAdminService(
				jdbc, store, mock(AiProviderRegistry.class),
				mock(AiProviderCredentialStore.class));

		var provider = service.updateProvider(
				"vendor", new AiProviderAdminService.UpdateProviderRequest(null, false));
		var model = service.updateModel(
				"primary", new AiProviderAdminService.UpdateModelRequest(
						null, false, null, null, null, null, null, null, null));

		assertThat(provider.enabled()).isFalse();
		assertThat(provider.configVersion()).isEqualTo(2);
		assertThat(model.enabled()).isFalse();
		assertThat(service.configuration().providers().getFirst().enabled()).isFalse();
		assertThat(service.configuration().models().stream()
				.filter(value -> value.modelId().equals("primary"))
				.findFirst().orElseThrow().enabled()).isFalse();
	}

	@Test
	void exposesSortedConfigurationAndRoutesForEveryCapability() {
		JdbcTemplate jdbc = database();
		jdbc.update("insert into ai_providers values "
				+ "('another', 'Another', 'another-adapter', 'https://another.example', false, 2000, 3000, 4, null, null, current_timestamp)");
		jdbc.update("insert into ai_models values "
				+ "('scoring-model', 'another', 'Scoring', 'SCORING', true, null, 'REQUESTS', 0, 0, 0, 0, 0, 0, 'USD', current_timestamp)");
		AiProviderAdminService service = service(jdbc, mock(AiProviderRegistry.class), mock(AiProviderCredentialStore.class));

		var configuration = service.configuration();

		assertThat(configuration.databaseBacked()).isTrue();
		assertThat(configuration.providers()).extracting(AiProviderAdminService.ProviderView::providerId)
				.containsExactly("another", "vendor");
		assertThat(configuration.models()).extracting(AiProviderAdminService.ModelView::modelId)
				.containsExactly("fallback", "primary", "scoring-model");
		assertThat(configuration.models()).filteredOn(model -> model.modelId().equals("scoring-model"))
				.singleElement().satisfies(model -> {
					assertThat(model.providerId()).isEqualTo("another");
					assertThat(model.capability()).isEqualTo(AiCapability.SCORING);
					assertThat(model.billingUnit()).isEqualTo("REQUESTS");
					assertThat(model.currency()).isEqualTo("USD");
				});
		assertThat(configuration.routes()).extracting(AiProviderAdminService.RouteView::capability)
				.containsExactly(AiCapability.REALTIME, AiCapability.LLM, AiCapability.SCORING,
						AiCapability.TTS, AiCapability.TRANSCRIPTION);
		assertThat(configuration.routes()).allSatisfy(route -> {
			assertThat(route.routeKey()).isEqualTo("default");
			assertThat(route.modelIds()).isNotNull();
		});
	}

	@Test
	void delegatesCredentialStatusAndReplacementOnlyAfterProviderExists() {
		JdbcTemplate jdbc = database();
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		AiProviderCredentialStore credentials = mock(AiProviderCredentialStore.class);
		when(credentials.status("vendor")).thenReturn(CREDENTIAL_STATUS);
		when(credentials.replace("vendor", Map.of("apiKey", "new-key"))).thenReturn(CREDENTIAL_STATUS);
		AiProviderAdminService service = service(jdbc, registry, credentials);

		assertThat(service.credentialStatus("vendor")).isSameAs(CREDENTIAL_STATUS);
		assertThat(service.replaceCredential("vendor", Map.of("apiKey", "new-key")))
				.isSameAs(CREDENTIAL_STATUS);

		verify(credentials).status("vendor");
		verify(credentials).replace("vendor", Map.of("apiKey", "new-key"));
		assertThatThrownBy(() -> service.credentialStatus("missing"))
				.isInstanceOf(BusinessException.class)
				.extracting(exception -> ((BusinessException) exception).code())
				.isEqualTo("AI_PROVIDER_NOT_FOUND");
		assertThatThrownBy(() -> service.replaceCredential("missing", Map.of("apiKey", "new-key")))
				.isInstanceOf(BusinessException.class)
				.extracting(exception -> ((BusinessException) exception).code())
				.isEqualTo("AI_PROVIDER_NOT_FOUND");
	}

	@Test
	void appliesProviderAndModelRequestDefaultsAndTrimsValues() {
		JdbcTemplate jdbc = database();
		AiProviderAdminService service = service(jdbc, mock(AiProviderRegistry.class), mock(AiProviderCredentialStore.class));

		var provider = service.updateProvider("vendor",
				new AiProviderAdminService.UpdateProviderRequest("  Renamed vendor  ", true));
		var model = service.updateModel("primary",
				new AiProviderAdminService.UpdateModelRequest(
						"  Renamed model  ", null, " mixed ",
						new BigDecimal("1.25"), null, null, null, null, new BigDecimal("0.5")));

		assertThat(provider.displayName()).isEqualTo("Renamed vendor");
		assertThat(provider.enabled()).isTrue();
		assertThat(provider.configVersion()).isEqualTo(2);
		assertThat(model.displayName()).isEqualTo("Renamed model");
		assertThat(model.enabled()).isTrue();
		assertThat(model.billingUnit()).isEqualTo("MIXED");
		assertThat(model.inputPricePerMillion()).isEqualByComparingTo("1.25");
		assertThat(model.outputPricePerMillion()).isZero();
		assertThat(model.requestPricePerCall()).isEqualByComparingTo("0.5");

		var retained = service.updateProvider("vendor",
				new AiProviderAdminService.UpdateProviderRequest("   ", null));
		var retainedModel = service.updateModel("primary",
				new AiProviderAdminService.UpdateModelRequest(
						" ", null, " ", null, null, null, null, null, null));
		assertThat(retained.displayName()).isEqualTo("Renamed vendor");
		assertThat(retained.enabled()).isTrue();
		assertThat(retainedModel.displayName()).isEqualTo("Renamed model");
		assertThat(retainedModel.billingUnit()).isEqualTo("MIXED");
	}

	@Test
	void rejectsMissingProviderAndModel() {
		AiProviderAdminService service = service(database(), mock(AiProviderRegistry.class), mock(AiProviderCredentialStore.class));

		assertCode("AI_PROVIDER_NOT_FOUND", () -> service.updateProvider(
				"missing", new AiProviderAdminService.UpdateProviderRequest(null, true)));
		assertCode("AI_MODEL_NOT_FOUND", () -> service.updateModel(
				"missing", new AiProviderAdminService.UpdateModelRequest(null, true, null, null, null, null, null, null, null)));
	}

	@Test
	void rejectsInvalidBillingUnitsAndNegativePrices() {
		AiProviderAdminService service = service(database(), mock(AiProviderRegistry.class), mock(AiProviderCredentialStore.class));

		assertCode("AI_BILLING_UNIT_INVALID", () -> service.updateModel("primary",
				new AiProviderAdminService.UpdateModelRequest(null, null, "unsupported", null, null, null, null, null, null)));
		assertCode("AI_MODEL_PRICE_INVALID", () -> service.updateModel("primary",
				new AiProviderAdminService.UpdateModelRequest(null, null, null, new BigDecimal("-0.01"), null, null, null, null, null)));
		assertCode("AI_MODEL_PRICE_INVALID", () -> service.updateModel("primary",
				new AiProviderAdminService.UpdateModelRequest(null, null, null, null, null, null, null, null, new BigDecimal("-1"))));
	}

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

	@Test
	void usesDefaultRouteKeyWhenOmittedAndTrimsModelIds() {
		JdbcTemplate jdbc = database();
		AiProviderRegistry registry = registryWithModels(
				new AiModelDefinition("primary", "vendor", AiCapability.LLM, true),
				new AiModelDefinition("fallback", "vendor", AiCapability.LLM, false));
		AiProviderAdminService service = service(jdbc, registry, mock(AiProviderCredentialStore.class));

		var route = service.replaceRoute(AiCapability.LLM,
				new AiProviderAdminService.UpdateRouteRequest(null, List.of(" primary ", "fallback")));

		assertThat(route.routeKey()).isEqualTo("default");
		assertThat(route.modelIds()).containsExactly("primary", "fallback");
		assertThat(jdbc.queryForList(
				"select model_id from ai_models where route_priority is not null order by route_priority",
				String.class)).containsExactly("primary", "fallback");
	}

	@Test
	void rejectsInvalidRouteKeyEmptyDuplicateAndBlankModelLists() {
		AiProviderAdminService service = service(database(), registryWithModels(
				new AiModelDefinition("primary", "vendor", AiCapability.LLM, true)),
				mock(AiProviderCredentialStore.class));

		assertCode("AI_ROUTE_KEY_UNSUPPORTED", () -> service.replaceRoute(AiCapability.LLM,
				new AiProviderAdminService.UpdateRouteRequest("secondary", List.of("primary"))));
		assertCode("AI_ROUTE_INVALID", () -> service.replaceRoute(AiCapability.LLM,
				new AiProviderAdminService.UpdateRouteRequest("default", null)));
		assertCode("AI_ROUTE_INVALID", () -> service.replaceRoute(AiCapability.LLM,
				new AiProviderAdminService.UpdateRouteRequest("default", List.of(" "))));
		assertCode("AI_ROUTE_INVALID", () -> service.replaceRoute(AiCapability.LLM,
				new AiProviderAdminService.UpdateRouteRequest("default", List.of("primary", " primary "))));
	}

	@Test
	void rejectsUnavailableCapabilityMismatchedAndDisabledRouteModels() {
		JdbcTemplate jdbc = database();
		jdbc.update("insert into ai_models values "
				+ "('disabled', 'vendor', 'Disabled', 'LLM', false, null, 'TOKENS', 0, 0, 0, 0, 0, 0, 'CNY', current_timestamp),"
				+ "('scoring-model', 'vendor', 'Scoring', 'SCORING', true, null, 'REQUESTS', 0, 0, 0, 0, 0, 0, 'CNY', current_timestamp)");
		AiProviderAdminService service = service(jdbc, registryWithModels(
				new AiModelDefinition("primary", "vendor", AiCapability.LLM, true),
				new AiModelDefinition("disabled", "vendor", AiCapability.LLM, false),
				new AiModelDefinition("scoring-model", "vendor", AiCapability.SCORING, true)),
				mock(AiProviderCredentialStore.class));

		assertCode("AI_ROUTE_MODEL_UNAVAILABLE", () -> service.replaceRoute(AiCapability.LLM,
				new AiProviderAdminService.UpdateRouteRequest("default", List.of("unknown"))));
		assertCode("AI_ROUTE_MODEL_UNAVAILABLE", () -> service.replaceRoute(AiCapability.LLM,
				new AiProviderAdminService.UpdateRouteRequest("default", List.of("scoring-model"))));
		assertCode("AI_ROUTE_MODEL_DISABLED", () -> service.replaceRoute(AiCapability.LLM,
				new AiProviderAdminService.UpdateRouteRequest("default", List.of("disabled"))));
	}

	private static AiProviderAdminService service(
			JdbcTemplate jdbc, AiProviderRegistry registry, AiProviderCredentialStore credentials) {
		return new AiProviderAdminService(jdbc, new JdbcAiConfigurationStore(jdbc), registry, credentials);
	}

	private static AiProviderRegistry registryWithModels(AiModelDefinition... models) {
		AiProviderRegistry registry = mock(AiProviderRegistry.class);
		when(registry.deployedModels()).thenReturn(List.of(models));
		return registry;
	}

	private static void assertCode(String code, ThrowingCallable executable) {
		assertThatThrownBy(executable)
				.isInstanceOf(BusinessException.class)
				.extracting(exception -> ((BusinessException) exception).code())
				.isEqualTo(code);
	}

	private static JdbcTemplate database() {
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setURL("jdbc:h2:mem:ai-provider-admin-" + UUID.randomUUID()
				+ ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
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
