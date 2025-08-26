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
    private final UserContextService userContextService; // ✅ eklendi
    private final BlockService blockService;             // ✅ eklendi

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
        User me = userContextService.getCurrentUser();
        User target = userRepository.findByNickname(nickname)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // ✅ beni engelleyen varsa profilini açtırma
        if (blockService.blocksMe(me.getId(), target.getId())) {
            throw new UsernameNotFoundException("User not found");
        }

        // (Ben target'ı engellemişsem yine görebilirim → unblock için)
        return toDTO(target);
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
