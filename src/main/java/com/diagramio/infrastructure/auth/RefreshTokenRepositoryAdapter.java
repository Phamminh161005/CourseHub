package com.diagramio.infrastructure.auth;

import com.diagramio.domain.auth.RefreshToken;
import com.diagramio.domain.auth.RefreshTokenRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Cài đặt RefreshTokenRepository của domain bằng Spring Data JPA. */
@Repository
public class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository jpaRepository;

    public RefreshTokenRepositoryAdapter(RefreshTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return jpaRepository.findByTokenHash(tokenHash).map(RefreshTokenEntity::toDomain);
    }

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        return jpaRepository.save(RefreshTokenEntity.fromDomain(refreshToken)).toDomain();
    }
}
