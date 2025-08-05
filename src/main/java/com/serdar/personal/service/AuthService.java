package com.serdar.personal.service;

import com.serdar.personal.exception.EmailAlreadyUsedException;
import com.serdar.personal.exception.InvalidCredentialsException;
import com.serdar.personal.exception.NicknameAlreadyUsedException;
import com.serdar.personal.exception.UserNotFoundException;
import com.serdar.personal.model.User;
import com.serdar.personal.model.dto.AuthRequest;
import com.serdar.personal.model.dto.AuthResponse;
import com.serdar.personal.model.dto.RegisterRequest;
import com.serdar.personal.model.Role;
import com.serdar.personal.repository.UserRepository;
import com.serdar.personal.security.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;

    @Value("${refresh.expiration.default}")
    private int refresh_expiration_default;

    @Value("${refresh.expiration.remember}")
    private int refresh_expiration_remember;

    @Value("${server.port}")
    private String port;

    @Value("${server.address}")
    private String address;

    @Value("${frontend.port}")
    private String front_port;

    @Value("${frontend.address}")
    private String front_address;

    public AuthResponse login(AuthRequest request, HttpServletResponse response) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(UserNotFoundException::new);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        if (!user.getActivated()) {
            throw new RuntimeException("Account is not activated. Check your email.");
        }

        int cookieAge = request.isRememberMe() ? refresh_expiration_remember : refresh_expiration_default;

        String token = jwtService.generateToken(user);

        String refreshToken = jwtService.generateRefreshToken(user, cookieAge);
        user.setRefreshToken(refreshToken);
        userRepository.save(user);

        Cookie cookie = new Cookie("refreshToken", refreshToken);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(cookieAge);
        response.addCookie(cookie);

        return new AuthResponse(token);

    }

    public ResponseEntity<String> register(RegisterRequest request) {
        boolean exists = userRepository.findByEmail(request.getEmail()).isPresent();
        if (exists) {
            throw new EmailAlreadyUsedException();
        }
        if (userRepository.findByNickname(request.getNickname()).isPresent()) {
            throw new NicknameAlreadyUsedException();
        }

        String activationCode = UUID.randomUUID().toString();

        User newUser = User.builder()
                .name(request.getName())
                .surname(request.getSurname())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .role(Role.USER)
                .activationCode(activationCode)
                .activated(false)
                .build();

        userRepository.save(newUser);

        String activationLink = "http://" + address + ":" + port + "/api/auth/activate?code=" + activationCode;
        emailService.sendEmail(newUser.getEmail(), "Activate your account",
                "Please click this link to activate: " + activationLink);


        return ResponseEntity.ok("Registration successful. Please check your email to activate your account.");

    }

    public String activate(String code) {
        User user = userRepository.findByActivationCode(code)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid activation code"));
        user.setActivated(true);
        user.setActivationCode(null);
        userRepository.save(user);
        return "Account activated successfully!";
    }

    public String logout(HttpServletResponse response, Authentication authentication) {
        if (authentication != null) {
            User user = (User) authentication.getPrincipal();
            user.setRefreshToken(null);
            userRepository.save(user);
        }

        ResponseCookie cookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true) // ilk gönderdiğin çerez secure true idi!
                .sameSite("Strict") // yine ilk ayarla aynı olmalı
                .path("/")
                .maxAge(0)
                .build();

        response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());


        return "Logged out successfully.";
    }

    public String forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));

        String resetCode = UUID.randomUUID().toString();
        user.setResetCode(resetCode);
        userRepository.save(user);

        String resetLink = "http://" + front_address + ":" + front_port + "/reset-password?code=" + resetCode;
        emailService.sendEmail(user.getEmail(), "Reset your password", "Reset link: " + resetLink);

        return "Reset link sent to your email.";
    }

    public String resetPassword(String code, String newPassword) {
        User user = userRepository.findByResetCode(code)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid reset code"));

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetCode(null);
        userRepository.save(user);

        return "Password reset successful.";
    }






}