package dev.campuspilot.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Runs on every request. If a valid Bearer token is present, it puts an
 * authenticated principal into the SecurityContext.
 *
 * <p>The important structural point: this filter only decides WHO the
 * caller claims to be. It never decides WHAT they may touch. That stays
 * in the service layer, where every query is scoped by the profile id
 * taken from the token. Splitting those two concerns is what stops an
 * authentication bug from becoming a data-exposure bug.
 *
 * <p>If the header is absent, the filter simply does nothing and the
 * request continues unauthenticated. Spring Security then rejects it at
 * the authorisation stage, which produces the correct 401. Silently
 * continuing is the right behaviour — public endpoints must still work.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwt;

    public JwtAuthFilter(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HEADER);

        if (header != null && header.startsWith(PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            String token = header.substring(PREFIX.length()).trim();

            try {
                UUID profileId = jwt.parseProfileId(token);
                String role = jwt.parseRole(token);

                // A marker principal. It carries the caller's identity and
                // role and nothing else — deliberately not the full user
                // record, so a controller cannot accidentally trust stale
                // claims such as an email that changed since signing.
                var principal = new AuthenticatedProfile(profileId, role);
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, List.of());
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (InvalidTokenException ex) {
                // Deliberately swallowed. Returning 401 here with a custom
                // body would bypass the standard Spring Security entry
                // point, and would make the failure modes inconsistent.
                // The authorisation stage below produces the 401.
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }
}