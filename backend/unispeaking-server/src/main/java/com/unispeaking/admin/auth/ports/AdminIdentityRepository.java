package com.unispeaking.admin.auth.ports;

import com.unispeaking.admin.auth.domain.AdminAccount;
import java.util.Optional;
import java.util.UUID;

public interface AdminIdentityRepository {
    Optional<AdminAccount> findByLogin(String login);

    Optional<AdminAccount> findById(UUID id);
}
