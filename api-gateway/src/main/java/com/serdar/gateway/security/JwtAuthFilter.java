package com.serdar.gateway.security;

import com.serdar.gateway.client.AuthClient;
import com.serdar.proto.auth.ValidateTokenResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Stateless auth filter: the only thing the gateway trusts per request is a
 * short-lived access token in the {@code Authorization: Bearer ...} header.
 *
 * - Missing / expired / invalid token → request proceeds unauthenticated, and
 *   Spring Security turns protected endpoints into a 401.
 * - The frontend reacts to the 401 by calling {@code POST /api/auth/refresh}
 *   (which reads the HttpOnly refresh cookie), gets a fresh access token, and
 *   retries the original request.
 *
 * This filter does NOT look at cookies. Refresh is an explicit, single
 * endpoint — not a side-effect of every request — which keeps logs sane and
 * makes 401 behavior predictable.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final AuthClient authClient;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                ValidateTokenResponse v = authClient.validate(header.substring(7));
                if (v.getValid()) {
                    AuthenticatedUser principal = new AuthenticatedUser(
                            v.getUserId(), v.getEmail(), v.getNickname(),
                            v.getRole().name(), v.getSessionId(), v.getFrozen());
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(new SimpleGrantedAuthority(principal.roleAuthority())));
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception ignored) { /* validation failed → request is unauthenticated */ }
        }
        chain.doFilter(req, res);
    }
}
