package com.diagramio.domain.user;

import java.util.Optional;
import java.util.UUID;

/** Cổng truy cập dữ liệu người dùng; tầng infrastructure cài đặt bằng JPA. */
public interface UserRepository {

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    User save(User user);
}
