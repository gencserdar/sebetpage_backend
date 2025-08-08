package com.serdar.personal.security;

import com.serdar.personal.model.User;
import com.serdar.personal.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.util.AntPathMatcher;          // ★ NEW

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final UserRepository userRepository;

    /* ----------  NEW : paths that completely skip this filter ---------- */
    private static final AntPathMatcher matcher = new AntPathMatcher();
    private static final List<String> SKIP_PATHS = List.of(
            "/api/auth/**",   // your existing public endpoints
            "/ws/**",         // SockJS handshake + transports (/ws/info, /ws/**/**)
            "/ws"             // raw endpoint hit by the initial handshake
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();     // use servlet path for matching
        return SKIP_PATHS.stream().anyMatch(p -> matcher.match(p, path));
    }

    /* ------------------------------------------------------------------ */

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        /* ---- existing logic stays exactly as-is ---- */
        String accessToken = null;
        final String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        }

        if (accessToken == null) {
            if (!tryRefreshToken(request, response)) {
                send401(response);
                return;
            }
        } else {
            try {
                String email = jwtService.extractUsername(accessToken);
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                if (jwtService.isTokenValid(accessToken, userDetails)) {
                    setAuth(userDetails, request);
                } else if (!tryRefreshToken(request, response)) {
                    send401(response);
                    return;
                }
            } catch (Exception e) {
                if (!tryRefreshToken(request, response)) {
                    send401(response);
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /* --------------- unchanged helper methods ------------------------- */

    private void send401(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Unauthorized\"}");
    }

    private boolean tryRefreshToken(HttpServletRequest request, HttpServletResponse response) {
        Optional<String> refreshTokenOpt = getRefreshTokenFromCookies(request);
        if (refreshTokenOpt.isEmpty()) return false;

        String refreshToken = refreshTokenOpt.get();
        Optional<User> userOpt = userRepository.findByRefreshToken(refreshToken);
        if (userOpt.isEmpty() || jwtService.isTokenExpired(refreshToken)) return false;

        try {
            User user = userOpt.get();
            String newAccessToken = jwtService.generateToken(user);
            UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
            setAuth(userDetails, request);
            response.setHeader("x-new-token", newAccessToken);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void setAuth(UserDetails userDetails, HttpServletRequest request) {
        var auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Optional<String> getRefreshTokenFromCookies(HttpServletRequest request) {
        if (request.getCookies() == null) return Optional.empty();
        return Arrays.stream(request.getCookies())
                .filter(c -> "refreshToken".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }
}
