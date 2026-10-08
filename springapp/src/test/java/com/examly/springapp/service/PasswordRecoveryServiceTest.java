package com.examly.springapp.service;

import com.examly.springapp.model.User;
import com.examly.springapp.repository.UserRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the Forgot Password / email OTP recovery flow:
 * sending, verification rules, OTP authentication state and password change.
 */
@ExtendWith(MockitoExtension.class)
class PasswordRecoveryServiceTest {

    private static final String EMAIL = "recovery@user.com";
    private static final String RAW_PASSWORD = "OldPass@123";

    @Mock
    private UserRepo userRepo;

    @Mock
    private EmailService emailService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private PasswordRecoveryService passwordRecoveryService;
    private User user;

    @BeforeEach
    void setUp() {
        passwordRecoveryService = new PasswordRecoveryService(userRepo, emailService, passwordEncoder);
        ReflectionTestUtils.setField(passwordRecoveryService, "expirationMinutes", 5L);
        ReflectionTestUtils.setField(passwordRecoveryService, "maxAttempts", 5);
        ReflectionTestUtils.setField(passwordRecoveryService, "resendCooldownSeconds", 60L);

        user = new User(1L, "recovery user", EMAIL,
                passwordEncoder.encode(RAW_PASSWORD), "9876543210", "User");
    }

    @Test
    void sendOtpForUnknownEmailIsRejected() {
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordRecoveryService.sendOtp(EMAIL))
                .isInstanceOf(IllegalArgumentException.class);

        verify(emailService, never()).sendPasswordRecoveryOtpEmail(any(), any());
    }

    @Test
    void sendOtpDeliversSixDigitCodeAndNeverReturnsIt() {
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        // The API surface is void: the OTP only ever reaches the mailbox.
        passwordRecoveryService.sendOtp(EMAIL);

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordRecoveryOtpEmail(org.mockito.ArgumentMatchers.eq(EMAIL),
                codeCaptor.capture());
        assertThat(codeCaptor.getValue()).matches("\\d{6}");
        assertThatThrownBy(() -> passwordRecoveryService.sendOtp(EMAIL))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void expiredOtpIsRejected() {
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        ReflectionTestUtils.setField(passwordRecoveryService, "expirationMinutes", 0L);

        passwordRecoveryService.sendOtp(EMAIL);

        assertThatThrownBy(() -> passwordRecoveryService.verifyOtp(EMAIL, "123456"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void wrongOtpIsRejectedUntilMaxAttemptsIsExceeded() {
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        ReflectionTestUtils.setField(passwordRecoveryService, "maxAttempts", 2);
        String code = currentOtp();

        assertThatThrownBy(() -> passwordRecoveryService.verifyOtp(EMAIL, "000000"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> passwordRecoveryService.verifyOtp(EMAIL, "000000"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> passwordRecoveryService.verifyOtp(EMAIL, code))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Maximum OTP attempts");
    }

    @Test
    void correctOtpAuthenticatesUserAndForcesPasswordChange() {
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String code = currentOtp();

        User authenticated = passwordRecoveryService.verifyOtp(EMAIL, code);

        assertThat(authenticated.getEmail()).isEqualTo(EMAIL);
        assertThat(authenticated.isMustChangePassword()).isTrue();

        // single use: the same OTP cannot be replayed
        assertThatThrownBy(() -> passwordRecoveryService.verifyOtp(EMAIL, code))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void otpCannotBeUsedForAnotherAccount() {
        User other = new User(2L, "other", "other@user.com",
                passwordEncoder.encode(RAW_PASSWORD), "9123456780", "User");
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        String code = currentOtp();

        assertThatThrownBy(() -> passwordRecoveryService.verifyOtp("other@user.com", code))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(other.isMustChangePassword()).isFalse();
    }

    @Test
    void passwordChangeRequiresVerifiedRecoverySession() {
        assertThatThrownBy(() -> passwordRecoveryService.changePassword(EMAIL, "NewPass@1234"))
                .isInstanceOf(IllegalStateException.class);
        verify(userRepo, never()).save(any(User.class));
    }

    @Test
    void passwordChangeAfterOtpLoginReplacesOldPasswordAndClearsRequirement() {
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepo.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        String code = currentOtp();
        passwordRecoveryService.verifyOtp(EMAIL, code);

        passwordRecoveryService.changePassword(EMAIL, "NewPass@1234");

        User saved = user;
        assertThat(passwordEncoder.matches("NewPass@1234", saved.getPassword())).isTrue();
        assertThat(passwordEncoder.matches(RAW_PASSWORD, saved.getPassword())).isFalse();
        assertThat(saved.isMustChangePassword()).isFalse();

        // recovery session is single use
        assertThatThrownBy(() -> passwordRecoveryService.changePassword(EMAIL, "Another@1234"))
                .isInstanceOf(IllegalStateException.class);
    }

    private String currentOtp() {
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.clearInvocations(emailService);
        passwordRecoveryService.sendOtp(EMAIL);
        verify(emailService).sendPasswordRecoveryOtpEmail(
                org.mockito.ArgumentMatchers.eq(EMAIL.toLowerCase(Locale.ROOT)), codeCaptor.capture());
        return codeCaptor.getValue();
    }
}
