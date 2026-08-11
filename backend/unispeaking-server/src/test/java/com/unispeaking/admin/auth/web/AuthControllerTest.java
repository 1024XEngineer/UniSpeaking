package com.unispeaking.admin.auth.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.jdbc.Sql;
import com.unispeaking.admin.auth.adapters.memory.InMemoryAdminIdentityRepository;
import com.unispeaking.admin.auth.domain.AdminAccount;
import com.unispeaking.admin.auth.domain.AdminRole;

@SpringBootTest(properties = {
        "unispeaking.admin.bootstrap-password=change-this-development-password",
        "unispeaking.admin.persistence=in-memory",
        "unispeaking.integrations.usage-source=postgres",
        "unispeaking.integrations.aliyun.sync-enabled=false",
        "UNISPEAKING_DB_URL=jdbc:h2:mem:admin-auth;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "UNISPEAKING_DB_USERNAME=sa",
        "UNISPEAKING_DB_PASSWORD="
})
@AutoConfigureMockMvc
@Sql(scripts = "classpath:db/admin-test-schema.sql")
class AuthControllerTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    InMemoryAdminIdentityRepository adminIdentities;

    @Autowired
    @Qualifier("adminPasswordEncoder")
    PasswordEncoder passwordEncoder;

    @Test
    void invalidCredentialsUseStableErrorEnvelope() throws Exception {
        mvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"missing\",\"password\":\"wrong password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID"))
                .andExpect(jsonPath("$.request_id").isNotEmpty());
    }

    @Test
    void loginSessionSupportsMeAndCannotBeReusedAfterLogout() throws Exception {
        var login = mvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"admin@unispeaking.local\",\"password\":\"change-this-development-password\"}"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Path=/")))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("SameSite=Strict")))
                .andReturn();
        var cookie = login.getResponse().getCookie("us-admin-session");

        mvc.perform(get("/api/admin/auth/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("admin@unispeaking.local"))
                .andExpect(jsonPath("$.role").value("SUPER_ADMIN"));

        mvc.perform(post("/api/admin/auth/logout").cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));

        mvc.perform(get("/api/admin/auth/me").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void superAdminCanUpdateUserEntitlementWithoutResettingCurrentUsage() throws Exception {
        var userId = java.util.UUID.fromString("11111111-1111-4111-8111-111111111111");
        jdbc.update("insert into app_users (id, email, password_hash, created_at) values (?, ?, ?, current_timestamp)",
                userId, "entitlement@example.com", "not-used");
        jdbc.update("insert into user_entitlements (user_id, quota_date, plan_code, plan_name, quota_seconds, used_seconds, status, updated_at) "
                        + "values (?, current_date, 'free', 'Free', 600, 125.5, 'active', current_timestamp)", userId);

        var login = mvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"admin@unispeaking.local\",\"password\":\"change-this-development-password\"}"))
                .andExpect(status().isNoContent())
                .andReturn();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                        "/api/admin/users/" + userId + "/entitlement")
                        .cookie(login.getResponse().getCookie("us-admin-session"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"pro\",\"planName\":\"Pro\",\"quotaSeconds\":3600,\"status\":\"active\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").value(userId.toString()))
                .andExpect(jsonPath("$.plan_code").value("pro"))
                .andExpect(jsonPath("$.quota_seconds").value(3600))
                .andExpect(jsonPath("$.used_seconds").value(125.5));
    }

    @Test
    void readOnlyAdministratorCannotUpdateUserEntitlement() throws Exception {
        adminIdentities.save(new AdminAccount(
                java.util.UUID.fromString("33333333-3333-4333-8333-333333333333"),
                "auditor@unispeaking.local",
                passwordEncoder.encode("auditor-test-password"),
                AdminRole.AUDITOR,
                true));
        var login = mvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"auditor@unispeaking.local\",\"password\":\"auditor-test-password\"}"))
                .andExpect(status().isNoContent())
                .andReturn();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                        "/api/admin/users/11111111-1111-4111-8111-111111111111/entitlement")
                        .cookie(login.getResponse().getCookie("us-admin-session"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planCode\":\"pro\",\"planName\":\"Pro\",\"quotaSeconds\":3600,\"status\":\"active\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void statusExceptionOnAdminRouteKeepsSessionAuthorization() throws Exception {
        var login = mvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"admin@unispeaking.local\",\"password\":\"change-this-development-password\"}"))
                .andExpect(status().isNoContent())
                .andReturn();

        mvc.perform(post("/api/admin/data-sources/aliyun-sls/sync")
                        .cookie(login.getResponse().getCookie("us-admin-session"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}
