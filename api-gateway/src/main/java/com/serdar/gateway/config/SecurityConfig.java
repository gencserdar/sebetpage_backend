package com.serdar.gateway.config;

import com.serdar.common.config.ProductionTransportValidator;
import com.serdar.gateway.security.FrozenAccountFilter;
import com.serdar.gateway.security.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final FrozenAccountFilter frozenAccountFilter;

    /** Comma-separated list of CORS origins, sourced from ALLOWED_ORIGINS in
     *  `.env`. No default — the gateway refuses to boot without it, which
     *  prevents accidentally shipping a wide-open or wrong-host CORS policy. */
    @Value("${app.allowed-origins}")
    private String allowedOriginsCsv;

    @Value("${app.environment}")
    private String environment;

    @Bean
    public SecurityFilterChain filter(HttpSecurity http) throws Exception {
        http
                .csrf(c -> c.disable())
                .cors(c -> c.configurationSource(corsSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> {
                    h.contentTypeOptions(c -> {});
                    h.frameOptions(f -> f.deny());
                    h.referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                    if (ProductionTransportValidator.isProductionLike(environment)) {
                        h.httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000));
                    }
                })
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/**", "/ws/**", "/actuator/health", "/uploads/**").permitAll()
                        .anyRequest().authenticated())
                // Default Spring Security returns 403 when the SecurityContext
                // is empty. Override to 401 so the frontend's apiService can
                // distinguish "not logged in / token expired" (retry via
                // /refresh) from a genuine forbidden (don't retry).
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorizedEntryPoint()))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(frozenAccountFilter, JwtAuthFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Unauthorized\"}");
        };
    }

    @Bean
    public CorsConfigurationSource corsSource() {
        // Origins come from ALLOWED_ORIGINS in `.env` (CSV). Trim each entry
        // so a stray space after a comma doesn't silently exclude an origin.
        List<String> origins = Arrays.stream(allowedOriginsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(origins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Requested-With"));
        cfg.setExposedHeaders(List.of("x-new-token"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
