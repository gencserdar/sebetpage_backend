package com.serdar.personal.controller;

import com.serdar.personal.exception.InvalidCredentialsException;
import com.serdar.personal.model.dto.AuthRequest;
import com.serdar.personal.model.dto.AuthResponse;
import com.serdar.personal.model.dto.RegisterRequest;
import com.serdar.personal.model.User;
import com.serdar.personal.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(authService.login(request, response));
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/refresh") // ✅ Changed to POST and added proper logic
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        try {
            System.out.println("Refresh endpoint called"); // ✅ Debug log
            AuthResponse authResponse = authService.refresh(request, response);

            // ✅ Set the new token in response header for frontend to catch
            response.setHeader("x-new-token", authResponse.getToken());

            return ResponseEntity.ok(authResponse);
        } catch (InvalidCredentialsException e) {
            System.out.println("Refresh failed: " + e.getMessage()); // ✅ Debug log
            return ResponseEntity.status(401).body(null);
        }
    }

    @GetMapping("/activate")
    public ResponseEntity<String> activate(@RequestParam String code) {
        return ResponseEntity.ok(authService.activate(code));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> logout(HttpServletResponse response, Authentication authentication) {
        return ResponseEntity.ok(authService.logout(response, authentication));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestParam String email) {
        return ResponseEntity.ok(authService.forgotPassword(email));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestParam String code, @RequestParam String newPassword) {
        return ResponseEntity.ok(authService.resetPassword(code, newPassword));
    }
}