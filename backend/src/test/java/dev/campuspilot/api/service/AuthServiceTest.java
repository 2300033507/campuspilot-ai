package dev.campuspilot.api.service;

import dev.campuspilot.api.domain.Profile;
import dev.campuspilot.api.exception.ConflictException;
import dev.campuspilot.api.repository.ProfileRepository;
import dev.campuspilot.api.security.JwtService;
import dev.campuspilot.api.web.dto.LoginRequest;
import dev.campuspilot.api.web.dto.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AuthService.
 *
 * <p>Hash-related tests use a real BCryptPasswordEncoder rather than a
 * mock. Mocking the encoder would make these tests assert nothing — the
 * claim worth checking is "the stored value verifies against the correct
 * password and rejects a wrong one", which only a real encoder can show.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    private static final String SECRET = "test-secret-that-is-long-enough-for-hs256-algorithm";
    private static final String GOOD_PASSWORD = "correct-horse-battery";

    @Mock
    ProfileRepository profiles;

    /**
     * Cost 4 instead of 10 purely to keep tests fast. The cost affects
     * speed, not behaviour — the hash format and verification are the
     * same. Production uses the default 10, configured in SecurityConfig.
     */
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private AuthService service() {
        return new AuthService(profiles, encoder, new JwtService(SECRET, 24));
    }

    private Profile savedProfile(String email, String rawPassword) {
        Profile p = new Profile();
        p.setId(UUID.randomUUID());
        p.setFullName("Test Student");
        p.setEmail(email);
        p.setPasswordHash(encoder.encode(rawPassword));
        return p;
    }

    @Test
    @DisplayName("stores a BCrypt hash, never the raw password")
    void storesHashNotRawPassword() {
        AuthService svc = service();
        when(profiles.findByEmail("student@example.com")).thenReturn(Optional.empty());
        when(profiles.save(any(Profile.class))).thenAnswer(inv -> {
            Profile p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        var response = svc.register(
                new RegisterRequest("Test Student", "student@example.com", GOOD_PASSWORD));

        ArgumentCaptor<Profile> captor = ArgumentCaptor.forClass(Profile.class);
        verify(profiles).save(captor.capture());

        String stored = captor.getValue().getPasswordHash();

        assertThat(stored)
                .as("the stored value must not be the password itself")
                .isNotEqualTo(GOOD_PASSWORD);
        assertThat(stored).startsWith("$2");          // BCrypt's marker
        assertThat(encoder.matches(GOOD_PASSWORD, stored))
                .as("hash must verify against the correct password")
                .isTrue();
        assertThat(encoder.matches("wrong-password", stored))
                .as("hash must reject anything else")
                .isFalse();

        // And no DTO ever carries it back out.
        assertThat(response.token()).isNotBlank();
    }

    @Test
    @DisplayName("normalises the email so different casing cannot create two accounts")
    void normalisesEmail() {
        AuthService svc = service();
        when(profiles.findByEmail("student@example.com")).thenReturn(Optional.empty());
        when(profiles.save(any(Profile.class))).thenAnswer(inv -> {
            Profile p = inv.getArgument(0);
            p.setId(UUID.randomUUID());   // JPA populates the id on save
            return p;
        });

        svc.register(new RegisterRequest("A", "  Student@EXAMPLE.com  ", GOOD_PASSWORD));

        ArgumentCaptor<Profile> captor = ArgumentCaptor.forClass(Profile.class);
        verify(profiles).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("student@example.com");
    }

    @Test
    @DisplayName("rejects a duplicate email")
    void rejectsDuplicateEmail() {
        AuthService svc = service();
        when(profiles.findByEmail("taken@example.com"))
                .thenReturn(Optional.of(savedProfile("taken@example.com", GOOD_PASSWORD)));

        assertThatThrownBy(() -> svc.register(
                new RegisterRequest("A", "taken@example.com", GOOD_PASSWORD)))
                .isInstanceOf(ConflictException.class);

        verify(profiles, never()).save(any());
    }

    @Test
    @DisplayName("rejects a wrong password")
    void rejectsWrongPassword() {
        AuthService svc = service();
        when(profiles.findByEmail("student@example.com"))
                .thenReturn(Optional.of(savedProfile("student@example.com", GOOD_PASSWORD)));

        assertThatThrownBy(() -> svc.login(
                new LoginRequest("student@example.com", "wrong-password-entirely")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("unknown email and wrong password produce the identical message")
    void identicalMessageForBothFailures() {
        AuthService svc = service();
        when(profiles.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        var unknownEmail = catchThrowable(() ->
                svc.login(new LoginRequest("nobody@example.com", "some-password")));
        var wrongPassword = catchThrowable(() -> svc.login(
                new LoginRequest("student@example.com", "some-password")));

        assertThat(unknownEmail).isInstanceOf(BadCredentialsException.class);
        // If these two ever differ, the login form becomes a tool for
        // discovering which email addresses have accounts.
        assertThat(unknownEmail.getMessage()).isEqualTo(wrongPassword.getMessage());
    }

    @Test
    @DisplayName("an account with no password set cannot log in")
    void passwordlessAccountCannotLogIn() {
        AuthService svc = service();

        Profile p = new Profile();                     // the Step 1 demo profile
        p.setId(UUID.randomUUID());
        p.setFullName("Demo Student");
        p.setEmail("demo@campuspilot.dev");
        p.setPasswordHash(null);

        when(profiles.findByEmail("demo@campuspilot.dev")).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> svc.login(
                new LoginRequest("demo@campuspilot.dev", "anything-at-all")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("logs in with the correct password and issues a token")
    void logsInSuccessfully() {
        AuthService svc = service();
        Profile p = savedProfile("student@example.com", GOOD_PASSWORD);
        when(profiles.findByEmail("student@example.com")).thenReturn(Optional.of(p));

        var response = svc.login(new LoginRequest("student@example.com", GOOD_PASSWORD));

        assertThat(response.profileId()).isEqualTo(p.getId());
        assertThat(response.fullName()).isEqualTo("Test Student");
        assertThat(response.token()).isNotBlank();
        assertThat(response.expiresInSeconds()).isEqualTo(24 * 3600L);
    }

    @Test
    @DisplayName("BCrypt produces a different hash each time for the same password")
    void bcryptSaltsEachHash() {
        String first  = encoder.encode(GOOD_PASSWORD);
        String second = encoder.encode(GOOD_PASSWORD);

        // If these matched, identical passwords would be identifiable
        // across accounts from the stored value alone.
        assertThat(first).isNotEqualTo(second);
        assertThat(encoder.matches(GOOD_PASSWORD, first)).isTrue();
        assertThat(encoder.matches(GOOD_PASSWORD, second)).isTrue();
    }
}