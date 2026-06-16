package com.serdar.gateway.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Static uploads are public; nosniff reduces MIME-sniffing risk on user-supplied images. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class UploadSecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (req.getRequestURI().startsWith("/uploads/")) {
            res.setHeader("X-Content-Type-Options", "nosniff");
        }
        chain.doFilter(req, res);
    }
}
