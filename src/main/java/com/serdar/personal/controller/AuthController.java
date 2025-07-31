package com.serdar.personal.controller;

import com.serdar.personal.exception.InvalidCredentialsException;
import com.serdar.personal.model.dto.AuthRequest;
import com.serdar.personal.model.dto.AuthResponse;
import com.serdar.personal.model.dto.RegisterRequest;
import com.serdar.personal.model.User;
import com.serdar.personal.repository.UserRepository;
import com.serdar.personal.security.JwtService;
import com.serdar.personal.service.AuthService;
import com.serdar.personal.service.EmailService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import java.util.Arrays;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(authService.login(request, response));
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @GetMapping("/me")
    public ResponseEntity<User> getMe(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(user);
    }

    @GetMapping("/refresh")
    public ResponseEntity<?> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = Arrays.stream(request.getCookies())
                .filter(c -> c.getName().equals("refreshToken"))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);

        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("No refresh token");
        }

        User user = userRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        String accessToken = jwtService.generateToken(user);
        return ResponseEntity.ok(new AuthResponse(accessToken));
    }

    @GetMapping("/activate")
    public ResponseEntity<String> activate(@RequestParam String code) {
        User user = userRepository.findByActivationCode(code)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid activation code"));

        user.setActivated(true);
        user.setActivationCode(null);
        userRepository.save(user);

        return ResponseEntity.ok("Account activated successfully!");
    }

    @GetMapping("/logout")
    public ResponseEntity<String> logout(HttpServletResponse response, Authentication authentication) {
        if (authentication != null) {
            User user = (User) authentication.getPrincipal();
            user.setRefreshToken(null);
            userRepository.save(user);
        }

        Cookie cookie = new Cookie("refreshToken", null);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        return ResponseEntity.ok("Logged out successfully.");
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestParam String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));

        String resetCode = UUID.randomUUID().toString();
        user.setResetCode(resetCode);
        userRepository.save(user);

        String resetLink = "http://localhost:8080/api/auth/reset-password?code=" + resetCode;
        emailService.sendActivationEmail(user.getEmail(), "Reset your password", "Reset link: " + resetLink);

        return ResponseEntity.ok("Reset link sent to your email.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestParam String code, @RequestParam String newPassword) {
        User user = userRepository.findByResetCode(code)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid reset code"));

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetCode(null);
        userRepository.save(user);

        return ResponseEntity.ok("Password reset successful.");
    }
}
