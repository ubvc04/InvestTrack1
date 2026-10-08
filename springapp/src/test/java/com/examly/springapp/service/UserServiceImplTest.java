package com.examly.springapp.service;

import com.examly.springapp.exceptions.InvestmentException;
import com.examly.springapp.model.User;
import com.examly.springapp.repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Normal (password) authentication must keep behaving exactly as before.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "Secret@123";

    @Mock
    private UserRepo userRepo;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userRepo, passwordEncoder);
    }

    @Test
    void loginSucceedsWithTheCorrectPassword() {
        User stored = new User(7L, "user", EMAIL,
                passwordEncoder.encode(PASSWORD), "9876543210", "User");
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(stored));

        User request = new User();
        request.setEmail("  USER@example.com ");
        request.setPassword(PASSWORD);

        User loggedIn = userService.loginUser(request);

        assertThat(loggedIn.getUserId()).isEqualTo(7L);
    }

    @Test
    void loginFailsWithAWrongPassword() {
        User stored = new User(7L, "user", EMAIL,
                passwordEncoder.encode(PASSWORD), "9876543210", "User");
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(stored));

        User request = new User();
        request.setEmail(EMAIL);
        request.setPassword("Wrong@123");

        assertThatThrownBy(() -> userService.loginUser(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void loginFailsForUnknownEmail() {
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.empty());

        User request = new User();
        request.setEmail(EMAIL);
        request.setPassword(PASSWORD);

        assertThatThrownBy(() -> userService.loginUser(request))
                .isInstanceOf(InvestmentException.class);
    }

    @Test
    void registrationEncodesPasswordAndForcesUserRole() {
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        User user = new User();
        user.setUsername("new user");
        user.setEmail(" New@Example.com ");
        user.setPassword(PASSWORD);
        user.setMobileNumber("9876543210");
        user.setUserRole("SuperAdmin"); // client supplied roles are ignored

        User created = userService.createUser(user);

        assertThat(created.getUserRole()).isEqualTo("User");
        assertThat(created.getEmail()).isEqualTo("new@example.com");
        assertThat(created.getPassword()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, created.getPassword())).isTrue();
        assertThat(created.isMustChangePassword()).isFalse();
    }

    @Test
    void adminCreationEncodesPasswordAndAssignsAdminRole() {
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        User user = new User();
        user.setUsername("admin");
        user.setEmail("admin@example.com");
        user.setPassword(PASSWORD);
        user.setMobileNumber("9876543210");

        User created = userService.createAdmin(user);

        assertThat(created.getUserRole()).isEqualTo("Admin");
        assertThat(passwordEncoder.matches(PASSWORD, created.getPassword())).isTrue();
    }

    @Test
    void changePasswordReplacesOldPasswordAndClearsTheRequirement() {
        User stored = new User(7L, "user", EMAIL,
                passwordEncoder.encode(PASSWORD), "9876543210", "User");
        stored.setMustChangePassword(true);
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(stored));
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> userService.changePassword(EMAIL, "BadOld", "New@Pass123"))
                .isInstanceOf(RuntimeException.class);

        userService.changePassword(EMAIL, PASSWORD, "New@Pass123");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(captor.capture());
        User saved = captor.getValue();
        assertThat(passwordEncoder.matches("New@Pass123", saved.getPassword())).isTrue();
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPassword())).isFalse();
        assertThat(saved.isMustChangePassword()).isFalse();
    }

    @Test
    void mustChangePasswordReflectsTheStoredFlag() {
        User stored = new User(7L, "user", EMAIL,
                passwordEncoder.encode(PASSWORD), "9876543210", "User");
        stored.setMustChangePassword(true);
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(stored));

        assertThat(userService.mustChangePassword(EMAIL)).isTrue();

        stored.setMustChangePassword(false);
        assertThat(userService.mustChangePassword(EMAIL)).isFalse();
    }
}
