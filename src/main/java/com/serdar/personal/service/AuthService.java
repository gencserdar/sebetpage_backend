package com.serdar.personal.service;

import com.serdar.personal.model.User;
import com.serdar.personal.model.dto.AuthRequest;
import com.serdar.personal.model.dto.AuthResponse;
import com.serdar.personal.model.dto.RegisterRequest;
import com.serdar.personal.model.Role;
import com.serdar.personal.repository.UserRepository;
import com.serdar.personal.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(token);
    }

    public AuthResponse register(RegisterRequest request) {
        boolean exists = userRepository.findByEmail(request.getEmail()).isPresent();
        if (exists) {
            throw new RuntimeException("Email already in use");
        }

        User newUser = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER) // default role
                .build();

        userRepository.save(newUser);
        String token = jwtService.generateToken(newUser);
        return new AuthResponse(token);
    }
}