package com.forgeci.server.repository;

import com.forgeci.server.entity.RefreshTokenEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    Optional<RefreshTokenEntity> findByFamilyAndRevokedFalse(UUID family);
}