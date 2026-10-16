package dev.campuspilot.api.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Tests for token creation and verification.
 *
 * <p>This is the security boundary. A mistake here means any token from
 * any account is accepted, so these cases are deliberately adversarial
 * rather than happy-path only.
 */
@DisplayName("JwtService")
class JwtServiceTest {

    private static final String SECRET = "a-secret-long-enough-to-satisfy-hs256-requirements";
    private static final String OTHER_SECRET = "a-completely-different-secret-of-sufficient-length";

    private final JwtService jwt = new JwtService(SECRET, 24);

    @Test
    @DisplayName("round-trips the profile id")
    void roundTripsProfileId() {
        UUID id = UUID.randomUUID();
        assertThat(jwt.parseProfileId(jwt.issue(id, "student"))).isEqualTo(id);
    }

    @Test
    @DisplayName("rejects a token signed with a different secret")
    void rejectsForeignSignature() {
        // A token from another server, or one an attacker forged with a
        // guessed secret. Must never be accepted.
        String forged = new JwtService(OTHER_SECRET, 24)
                .issue(UUID.randomUUID(), "admin");

        assertThatThrownBy(() -> jwt.parseProfileId(forged))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("rejects a token whose signature was altered")
    void rejectsTamperedPayload() {
        String token = jwt.issue(UUID.randomUUID(), "student");
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "AAAA";

        assertThatThrownBy(() -> jwt.parseProfileId(tampered))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("rejects garbage without throwing anything unexpected")
    void rejectsGarbage() {
        for (String bad : new String[] { "not-a-token", "", "   ", "a.b.c" }) {
            assertThatThrownBy(() -> jwt.parseProfileId(bad))
                    .as("input: '%s'", bad)
                    .isInstanceOf(InvalidTokenException.class);
        }
    }

    @Test
    @DisplayName("refuses to start with a short secret")
    void refusesShortSecret() {
        // HS256 requires 256 bits. A short secret is not a weak token, it
        // is a broken one — and silently accepting it would ship a
        // crackable deployment.
        assertThatThrownBy(() -> new JwtService("too-short", 24))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("every failure mode reports the identical message")
    void allFailuresShareOneMessage() {
        // The real invariant is not "these words must not appear" — it is
        // that a caller cannot distinguish WHY a token failed. Telling an
        // attacker "expired" versus "forged" is free information when
        // probing an endpoint.
        String foreign = new JwtService(OTHER_SECRET, 24)
                .issue(UUID.randomUUID(), "student");

        Function<String, String> messageFor = token ->
                catchThrowable(() -> jwt.parseProfileId(token)).getMessage();

        String reference = messageFor.apply(foreign);

        assertThat(reference)
                .isEqualTo(messageFor.apply("not-a-token"))
                .isEqualTo(messageFor.apply(""))
                .isEqualTo(messageFor.apply("a.b.c"))
                .isEqualTo(messageFor.apply(
                        foreign.substring(0, foreign.lastIndexOf('.') + 1) + "AAAA"))
                .isEqualTo(InvalidTokenException.missing().getMessage());
    }

    @Test
    @DisplayName("reports its own expiry so the client knows when to refresh")
    void reportsTtl() {
        assertThat(jwt.ttlSeconds()).isEqualTo(24 * 3600L);
    }
}