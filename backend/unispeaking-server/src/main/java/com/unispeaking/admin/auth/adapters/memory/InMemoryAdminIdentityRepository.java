package com.unispeaking.admin.auth.adapters.memory;

import com.unispeaking.admin.auth.domain.AdminAccount;
import com.unispeaking.admin.auth.ports.AdminIdentityRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryAdminIdentityRepository implements AdminIdentityRepository {
    private final Map<UUID, AdminAccount> accounts = new ConcurrentHashMap<>();

    public void save(AdminAccount account) {
        accounts.put(account.id(), account);
    }

    @Override
    public Optional<AdminAccount> findByLogin(String login) {
        return accounts.values().stream()
                .filter(account -> account.login().equalsIgnoreCase(login))
                .findFirst();
    }

    @Override
    public Optional<AdminAccount> findById(UUID id) {
        return Optional.ofNullable(accounts.get(id));
    }
}
