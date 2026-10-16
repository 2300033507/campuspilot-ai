package dev.campuspilot.api.service;

import dev.campuspilot.api.domain.Profile;
import dev.campuspilot.api.exception.ConflictException;
import dev.campuspilot.api.repository.ProfileRepository;
import dev.campuspilot.api.security.JwtService;
import dev.campuspilot.api.web.dto.AuthResponse;
import dev.campuspilot.api.web.dto.LoginRequest;
import dev.campuspilot.api.web.dto.RegisterRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Registration and login.
 */
@Service
public class AuthService {

    private final ProfileRepository profiles;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;

    public AuthService(ProfileRepository profiles,
                       PasswordEncoder passwordEncoder,
                       JwtService jwt) {
        this.profiles = profiles;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (profiles.findByEmail(email).isPresent()) {
            throw new ConflictException("An account with that email already exists");
        }

        Profile p = new Profile();
        p.setFullName(request.fullName().trim());
        p.setEmail(email);
        p.setRole("student");
        // Hash here, once. The raw password is never stored, logged, or
        // returned, and never leaves this method.
        p.setPasswordHash(passwordEncoder.encode(request.password()));

        Profile saved = profiles.save(p);

        return new AuthResponse(
                saved.getId(),
                saved.getFullName(),
                saved.getRole(),
                jwt.issue(saved.getId(), saved.getRole()),
                jwt.ttlSeconds());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        Profile p = profiles.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Email or password is incorrect"));

        if (p.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), p.getPasswordHash())) {
            // One message for "no such email" and for "wrong password".
            // Distinguishing them turns the login form into a tool for
            // discovering which emails have accounts.
            throw new BadCredentialsException("Email or password is incorrect");
        }

        return new AuthResponse(
                p.getId(),
                p.getFullName(),
                p.getRole(),
                jwt.issue(p.getId(), p.getRole()),
                jwt.ttlSeconds());
    }
}