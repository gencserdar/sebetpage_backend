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

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final UserRepository userRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {



        // Extract access token from Authorization header
        String accessToken = null;
        final String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        }

        // If no access token, check refresh token immediately
        if (accessToken == null) {
            if (!tryRefreshToken(request, response)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"Unauthorized\"}");
                response.setContentType("application/json");
                return; // Don't continue the filter chain
            }
        } else {
            // Check if access token is valid
            try {
                String email = jwtService.extractUsername(accessToken);
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                if (jwtService.isTokenValid(accessToken, userDetails)) {
                    // Access token is valid - authenticate and proceed
                    setAuth(userDetails, request);
                } else {
                    // Access token is invalid/expired - try refresh
                    if (!tryRefreshToken(request, response)) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.getWriter().write("{\"error\":\"Unauthorized\"}");
                        response.setContentType("application/json");
                        return; // Don't continue the filter chain
                    }
                }
            } catch (Exception e) {
                // Access token is malformed/expired - try refresh
                if (!tryRefreshToken(request, response)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("{\"error\":\"Unauthorized\"}");
                    response.setContentType("application/json");
                    return; // Don't continue the filter chain
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean tryRefreshToken(HttpServletRequest request, HttpServletResponse response) {
        Optional<String> refreshTokenOpt = getRefreshTokenFromCookies(request);

        if (refreshTokenOpt.isEmpty()) {
            return false; // No refresh token available
        }

        String refreshToken = refreshTokenOpt.get();
        Optional<User> userOpt = userRepository.findByRefreshToken(refreshToken);

        if (userOpt.isEmpty()) {
            return false; // Refresh token not found in database
        }

        User user = userOpt.get();

        // Check if refresh token is expired
        if (jwtService.isTokenExpired(refreshToken)) {
            return false; // Refresh token is expired
        }

        try {
            // Generate new access token
            String newAccessToken = jwtService.generateToken(user);

            // Set authentication context
            UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
            setAuth(userDetails, request);

            // Send new token to client
            response.setHeader("x-new-token", newAccessToken);

            return true; // Successfully refreshed
        } catch (Exception e) {
            return false; // Failed to generate new token
        }
    }

    private void setAuth(UserDetails userDetails, HttpServletRequest request) {
        var auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()
        );
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