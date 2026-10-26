package com.diagramio.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.diagramio.domain.auth.PasswordHasher;
import com.diagramio.domain.exception.InvalidInputException;
import com.diagramio.domain.exception.NotFoundException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UserServiceTest {

    private static final User USER = new User(UUID.randomUUID(), "uet", "uet@gmail.com", "hashed:old-pass",
            Instant.parse("2026-01-01T00:00:00Z"));

    private UserRepository userRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        PasswordHasher passwordHasher = new PasswordHasher() {
            @Override
            public String hash(String rawPassword) {
                return "hashed:" + rawPassword;
            }

            @Override
            public boolean matches(String rawPassword, String passwordHash) {
                return passwordHash.equals("hashed:" + rawPassword);
            }
        };
        userService = new UserService(userRepository, passwordHasher);
    }

    @Test
    void getByIdReturnsUser() {
        when(userRepository.findById(USER.id())).thenReturn(Optional.of(USER));

        assertThat(userService.getById(USER.id())).isEqualTo(USER);
    }

    @Test
    void getByIdThrowsWhenMissing() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void changePasswordStoresNewHash() {
        when(userRepository.findById(USER.id())).thenReturn(Optional.of(USER));

        userService.changePassword(USER.id(), "old-pass", "new-pass");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().passwordHash()).isEqualTo("hashed:new-pass");
        assertThat(saved.getValue().id()).isEqualTo(USER.id());
        assertThat(saved.getValue().createdAt()).isEqualTo(USER.createdAt());
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        when(userRepository.findById(USER.id())).thenReturn(Optional.of(USER));

        assertThatThrownBy(() -> userService.changePassword(USER.id(), "wrong", "new-pass"))
                .isInstanceOf(InvalidInputException.class);
        verify(userRepository, never()).save(any());
    }
}
