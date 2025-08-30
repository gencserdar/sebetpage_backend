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
import org.springframework.util.AntPathMatcher;

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

    private static final AntPathMatcher matcher = new AntPathMatcher();
    private static final List<String> SKIP_PATHS = List.of(
            "/api/auth/login",           // public login
            "/api/auth/register",        // public register
            "/api/auth/activate",        // public activation
            "/api/auth/forgot-password", // public forgot password
            "/api/auth/reset-password",  // public reset password
            "/api/auth/refresh",         // ✅ Skip refresh endpoint - handle it in controller
            "/ws/**",                    // WebSocket paths
            "/ws"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        boolean shouldSkip = SKIP_PATHS.stream().anyMatch(p -> matcher.match(p, path));
        if (shouldSkip) {
            System.out.println("Skipping filter for path: " + path); // ✅ Debug log
        }
        return shouldSkip;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        System.out.println("JWT Filter processing: " + request.getServletPath()); // ✅ Debug log

        String accessToken = null;
        final String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
            System.out.println("Found access token in header"); // ✅ Debug log
        }

        if (accessToken == null) {
            System.out.println("No access token, trying refresh..."); // ✅ Debug log
            if (!tryRefreshToken(request, response)) {
                System.out.println("Refresh failed, sending 401"); // ✅ Debug log
                send401(response);
                return;
            }
        } else {
            try {
                String email = jwtService.extractUsername(accessToken);
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                if (jwtService.isTokenValid(accessToken, userDetails)) {
                    System.out.println("Access token valid for user: " + email); // ✅ Debug log
                    setAuth(userDetails, request);
                } else {
                    System.out.println("Access token invalid, trying refresh..."); // ✅ Debug log
                    if (!tryRefreshToken(request, response)) {
                        send401(response);
                        return;
                    }
                }
            } catch (Exception e) {
                System.out.println("Error validating token: " + e.getMessage()); // ✅ Debug log
                if (!tryRefreshToken(request, response)) {
                    send401(response);
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private void send401(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Unauthorized\"}");
    }

    private boolean tryRefreshToken(HttpServletRequest request, HttpServletResponse response) {
        Optional<String> refreshTokenOpt = getRefreshTokenFromCookies(request);
        if (refreshTokenOpt.isEmpty()) {
            System.out.println("No refresh token found in cookies"); // ✅ Debug log
            return false;
        }

        String refreshToken = refreshTokenOpt.get();
        System.out.println("Found refresh token: " + refreshToken.substring(0, Math.min(10, refreshToken.length())) + "..."); // ✅ Debug log

        Optional<User> userOpt = userRepository.findByRefreshToken(refreshToken);
        if (userOpt.isEmpty()) {
            System.out.println("User not found for refresh token"); // ✅ Debug log
            return false;
        }

        if (jwtService.isTokenExpired(refreshToken)) {
            System.out.println("Refresh token expired"); // ✅ Debug log
            return false;
        }

        try {
            User user = userOpt.get();
            String newAccessToken = jwtService.generateToken(user);
            UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
            setAuth(userDetails, request);
            response.setHeader("x-new-token", newAccessToken);
            System.out.println("Token refreshed successfully for user: " + user.getEmail()); // ✅ Debug log
            return true;
        } catch (Exception e) {
            System.out.println("Error during token refresh: " + e.getMessage()); // ✅ Debug log
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