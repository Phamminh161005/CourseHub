package com.diagramio.domain.user;

import com.diagramio.domain.auth.PasswordHasher;
import com.diagramio.domain.exception.InvalidInputException;
import com.diagramio.domain.exception.NotFoundException;
import java.util.UUID;

/** Nghiệp vụ tài khoản của người đang đăng nhập: xem thông tin, đổi mật khẩu. */
public class UserService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public UserService(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    public User getById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = getById(userId);
        if (!passwordHasher.matches(currentPassword, user.passwordHash())) {
            throw new InvalidInputException("Current password is incorrect");
        }
        userRepository.save(user.withPasswordHash(passwordHasher.hash(newPassword)));
    }
}
