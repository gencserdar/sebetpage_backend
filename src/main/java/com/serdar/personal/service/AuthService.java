package com.serdar.personal.service;

import com.serdar.personal.exception.EmailAlreadyUsedException;
import com.serdar.personal.exception.InvalidCredentialsException;
import com.serdar.personal.exception.UserNotFoundException;
import com.serdar.personal.model.User;
import com.serdar.personal.model.dto.AuthRequest;
import com.serdar.personal.model.dto.AuthResponse;
import com.serdar.personal.model.dto.RegisterRequest;
import com.serdar.personal.model.Role;
import com.serdar.personal.repository.UserRepository;
import com.serdar.personal.security.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;


    @Value("${refresh.expiration.default}")
    private int refresh_expiration_default;

    @Value("${refresh.expiration.remember}")
    private int refresh_expiration_remember;

    public AuthResponse login(AuthRequest request, HttpServletResponse response) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(UserNotFoundException::new);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        if (!user.isActivated()) {
            throw new RuntimeException("Account is not activated. Check your email.");
        }

        int cookieAge = request.isRememberMe() ? refresh_expiration_remember : refresh_expiration_default;

        String token = jwtService.generateToken(user);

        String refreshToken = UUID.randomUUID().toString();
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

        String activationCode = UUID.randomUUID().toString();

        User newUser = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .activationCode(activationCode)
                .activated(false)
                .build();

        userRepository.save(newUser);

        //sendEmail(newUser.getEmail(), activationCode);

        return ResponseEntity.ok("Registration successful. Please check your email to activate your account.");

    }



}