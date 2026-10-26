package com.diagramio.infrastructure.user;

import com.diagramio.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Ánh xạ bảng users. updated_at không map, để DB tự điền giá trị mặc định. */
@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    protected UserEntity() {
        // JPA cần constructor không tham số
    }

    static UserEntity fromDomain(User user) {
        UserEntity entity = new UserEntity();
        entity.id = user.id();
        entity.username = user.username();
        entity.email = user.email();
        entity.passwordHash = user.passwordHash();
        entity.createdAt = user.createdAt();
        return entity;
    }

    User toDomain() {
        return new User(id, username, email, passwordHash, createdAt);
    }
}
