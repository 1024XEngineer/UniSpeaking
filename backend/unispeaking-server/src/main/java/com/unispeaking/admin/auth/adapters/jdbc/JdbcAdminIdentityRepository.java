package com.unispeaking.admin.auth.adapters.jdbc;

import com.unispeaking.admin.auth.domain.AdminAccount;
import com.unispeaking.admin.auth.domain.AdminRole;
import com.unispeaking.admin.auth.ports.AdminIdentityRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcAdminIdentityRepository implements AdminIdentityRepository {
    private final JdbcTemplate jdbc;

    public JdbcAdminIdentityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(AdminAccount account, Instant createdAt) {
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "insert into admin_accounts (id, login, password_hash, role, enabled, created_at) values (?, ?, ?, ?, ?, ?)");
            statement.setObject(1, account.id());
            statement.setString(2, account.login());
            statement.setString(3, account.passwordHash());
            statement.setString(4, account.role().name());
            statement.setBoolean(5, account.enabled());
            statement.setTimestamp(6, Timestamp.from(createdAt));
            return statement;
        });
    }

    public void saveIfAbsent(AdminAccount account, Instant createdAt) {
        try {
            save(account, createdAt);
        } catch (DuplicateKeyException ignored) {
            // Bootstrap is idempotent when the administrator already exists.
        }
    }

    public void saveOrUpdateBootstrap(AdminAccount account, Instant createdAt) {
        int updated = jdbc.update(
                "update admin_accounts set login = ?, password_hash = ?, role = ?, enabled = ? "
                        + "where id = ? or lower(login) = lower(?)",
                account.login(), account.passwordHash(), account.role().name(), account.enabled(),
                account.id(), account.login());
        if (updated == 0) {
            save(account, createdAt);
        }
    }

    @Override
    public Optional<AdminAccount> findByLogin(String login) {
        var rows = jdbc.query(
                "select id, login, password_hash, role, enabled from admin_accounts where lower(login) = lower(?)",
                (rs, row) -> new AdminAccount(
                        rs.getObject("id", UUID.class), rs.getString("login"), rs.getString("password_hash"),
                        AdminRole.valueOf(rs.getString("role")), rs.getBoolean("enabled")),
                login);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<AdminAccount> findById(UUID id) {
        var rows = jdbc.query(
                "select id, login, password_hash, role, enabled from admin_accounts where id = ?",
                (rs, row) -> new AdminAccount(
                        rs.getObject("id", UUID.class), rs.getString("login"), rs.getString("password_hash"),
                        AdminRole.valueOf(rs.getString("role")), rs.getBoolean("enabled")),
                id);
        return rows.stream().findFirst();
    }
}
