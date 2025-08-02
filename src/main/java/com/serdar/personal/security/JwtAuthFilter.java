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
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        String accessToken = authHeader != null && authHeader.startsWith("Bearer ")
                ? authHeader.substring(7) : null;

        boolean newTokenProduced = false;
        String  newToken         = null;

        if (accessToken != null) {
            String email;
            try {
                email = jwtService.extractUsername(accessToken);
            } catch (Exception e) {
                email = null;
            }

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                var userDetails = userDetailsService.loadUserByUsername(email);

                if (jwtService.isTokenValid(accessToken, userDetails)) {
                    setAuth(userDetails, request);
                    filterChain.doFilter(request, response);
                    return;
                }
            }
        }

        Optional<String> refreshOpt = getRefreshTokenFromCookies(request);
        if (refreshOpt.isPresent()) {
            Optional<User> userOpt = userRepository.findByRefreshToken(refreshOpt.get());

            if (userOpt.isPresent()) {
                User user = userOpt.get();
                newToken = jwtService.generateToken(user);
                newTokenProduced = true;

                setAuth(userDetailsService.loadUserByUsername(user.getEmail()), request);
            }
        }

        filterChain.doFilter(request, response);

        if (newTokenProduced) {
            response.setHeader("X-New-Token", newToken);
            if (response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
                response.setStatus(HttpServletResponse.SC_OK); // logout olmasın
            }
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    /* ─────────────────────────────────────────────────────────── */
    private void setAuth(Object userDetails, HttpServletRequest req) {
        var auth = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                ((UserDetails) userDetails).getAuthorities()
        );
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
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
