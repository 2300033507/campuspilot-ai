package dev.campuspilot.api.config;

// Spring Boot 4 ships Jackson 3, which moved from the
// com.fasterxml.jackson namespace to tools.jackson. The annotations
// package is the one exception and stays on com.fasterxml.jackson.
import tools.jackson.databind.ObjectMapper;
import dev.campuspilot.api.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stateless security.
 *
 * <p>"Stateless" means no server-side session. Every request carries its
 * own proof of identity in the Authorization header, so the server can be
 * restarted or scaled to several instances mid-request without breaking
 * anyone. That is the reason to choose JWT over sessions.
 *
 * <p>The trade-off, stated honestly: a session can be revoked instantly,
 * a stateless token cannot. Until it expires, a stolen token works. That
 * is why the TTL is short (24h) rather than long, and why real systems
 * add a refresh-token allowlist for revocation. Knowing the weakness of
 * your own design is a better signal than claiming it has none.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Endpoints reachable without a token. Everything else needs one. */
    private static final String[] PUBLIC_POST_ENDPOINTS = {
            "/api/auth/register",
            "/api/auth/login",
    };

    private static final String[] PUBLIC_GET_ENDPOINTS = {
            "/actuator/health",
    };

    private final JwtAuthFilter jwtAuthFilter;
    private final ObjectMapper json;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, ObjectMapper json) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.json = json;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // No browser session is ever created, so there is no session
            // cookie for a cross-site request to abuse. Disabling CSRF is
            // correct here precisely BECAUSE we are stateless — it would
            // be a serious vulnerability in a cookie-based design.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, PUBLIC_POST_ENDPOINTS).permitAll()
                .requestMatchers(HttpMethod.GET, PUBLIC_GET_ENDPOINTS).permitAll()
                .anyRequest().authenticated()
            )
            // These fire BEFORE any controller, so a 401 never reaches
            // GlobalExceptionHandler. They write the same ApiError shape
            // it produces, so a client sees one error format everywhere.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> writeError(
                        res, 401, "Unauthorized",
                        "Authentication required. Send: Authorization: Bearer <token>",
                        req.getRequestURI()))
                .accessDeniedHandler((req, res, e) -> writeError(
                        res, 403, "Forbidden",
                        "You do not have access to this resource",
                        req.getRequestURI()))
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt at cost 10 (the default).
     *
     * <p>Cost is the number of rounds, deliberately slow — roughly 100ms
     * per hash. That slowness is the entire point: it makes an offline
     * cracking of a stolen hash expensive while costing an attacker
     * thousands of times more.
     *
     * <p>Never use a plain hash like SHA-256 for passwords. Those were
     * built to be fast, which is precisely the wrong property here.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private void writeError(HttpServletResponse res, int status, String error,
                            String message, String path) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status);
        body.put("error", error);
        body.put("message", message);
        body.put("path", path);

        json.writeValue(res.getWriter(), body);
    }
}