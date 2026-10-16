package dev.campuspilot.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Creates and verifies JSON Web Tokens.
 *
 * <p>What a token carries: the profile id, the role, and an expiry. That
 * is the whole point of a stateless token — the server can trust
 * "student 42" without a database lookup on every request.
 *
 * <p>What it deliberately does NOT carry: the password, the email, or
 * anything else sensitive. A JWT is signed, not encrypted, so anyone
 * holding it can read its contents. Treat it as a label on a sealed
 * envelope, not a secret in itself.
 */
@Service
public class JwtService {

    /**
     * HS256 requires at least a 256-bit key. A short secret is not a
     * weaker token, it is a broken one — the library rejects it outright
     * rather than letting you ship something crackable.
     */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.ttl-hours:24}") long ttlHours) {

        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes for HS256. "
                  + "Generate one with: openssl rand -base64 48");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.ttl = Duration.ofHours(ttlHours);
    }

    public String issue(UUID profileId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(profileId.toString())
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public long ttlSeconds() {
        return ttl.toSeconds();
    }

    /**
     * Reads a single claim out of a token.
     *
     * <p>Same reasoning as parseProfileId: any failure throws, so a
     * caller cannot forget to handle the bad case.
     */
    public String parseRole(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Object role = claims.get("role");
            // Default to the LEAST privileged role if the claim is absent.
            // Assuming admin on a missing claim would be the opposite.
            return role == null ? "student" : role.toString();

        } catch (JwtException | IllegalArgumentException ex) {
            throw InvalidTokenException.missing();
        }
    }

    /**
     * Returns the profile id from a token, or throws.
     *
     * <p>Throws on every failure mode — expired, tampered signature,
     * malformed — rather than returning null. Callers therefore cannot
     * forget to handle the bad case, which is how a security check
     * accidentally ends up optional.
     */
    public UUID parseProfileId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return UUID.fromString(claims.getSubject());

        } catch (JwtException | IllegalArgumentException ex) {
            // One fixed message for every failure mode — wrong signature,
            // tampered payload, malformed, expired, missing. A caller must
            // not be able to tell those apart, because doing so hands an
            // attacker free information when probing the endpoint.
            throw InvalidTokenException.missing();
        }
    }
}