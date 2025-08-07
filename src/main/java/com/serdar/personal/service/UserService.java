package com.serdar.personal.service;

import com.serdar.personal.model.dto.UserDTO;
import com.serdar.personal.model.User;
import com.serdar.personal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /* ───────────── Public API ───────────── */
    public UserDTO getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return toDTO(user);
    }

    public UserDTO getByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        return toDTO(user);
    }

    public UserDTO getByNickname(String nickname) {
        User user = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return toDTO(user);
    }

    public UserDTO getById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        return toDTO(user);
    }

    /* ───────────── Helpers ───────────── */

    public UserDTO toDTO(User u) {
        return new UserDTO(
                u.getId(),
                u.getEmail(),
                u.getName(),
                u.getSurname(),
                u.getActivated(),
                u.getRole(),
                u.getNickname(),
                u.getProfileImageUrl()
        );
    }
}
