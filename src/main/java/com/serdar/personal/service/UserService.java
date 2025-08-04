package com.serdar.personal.service;

import com.serdar.personal.model.dto.UserDTO;
import com.serdar.personal.model.User;
import com.serdar.personal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /* ───────────── Public API ───────────── */

    public UserDTO getByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        return toDTO(user);
    }

    public UserDTO getById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        return toDTO(user);
    }

    /* ───────────── Helpers ───────────── */

    private UserDTO toDTO(User u) {
        return new UserDTO(
                u.getId(),
                u.getEmail(),
                u.getFullName(),
                u.getActivated(),
                u.getRole()
        );
    }
}
