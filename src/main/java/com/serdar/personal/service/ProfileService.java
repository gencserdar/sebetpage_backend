package com.serdar.personal.service;

import com.serdar.personal.exception.*;
import com.serdar.personal.model.dto.ChangePasswordRequest;
import com.serdar.personal.model.User;
import com.serdar.personal.model.dto.FieldUpdateResponse;
import com.serdar.personal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final S3Service s3Service;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // ✅ Profil fotoğrafı yükleme
    public FieldUpdateResponse uploadProfilePhoto(MultipartFile file) {
        validateImage(file);

        try {
            String imageUrl = s3Service.uploadFile(file);

            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new UserNotFoundException("User not found"));

            user.setProfileImageUrl(imageUrl);
            userRepository.save(user);

            return new FieldUpdateResponse("profileImageUrl", imageUrl);

        } catch (IOException e) {
            throw new RuntimeException("File upload failed", e);
        }
    }

    private void validateImage(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ImageValidationException("File is empty");
        }

        long maxSize = 5 * 1024 * 1024; // 5 MB
        if (file.getSize() > maxSize) {
            throw new ImageValidationException("File size exceeds 5 MB limit");
        }

        List<String> allowedExtensions = List.of("jpg", "jpeg", "png");
        String originalName = file.getOriginalFilename();
        if (originalName == null || !hasAllowedExtension(originalName, allowedExtensions)) {
            throw new ImageValidationException("Only JPG, JPEG, PNG files are allowed");
        }
    }

    private boolean hasAllowedExtension(String fileName, List<String> allowedExtensions) {
        String lowerName = fileName.toLowerCase();
        return allowedExtensions.stream().anyMatch(lowerName::endsWith);
    }

    // ✅ Şifre değiştirme
    public void changePassword(ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new InvalidCredentialsException("New password and confirmation do not match");
        }

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Old password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    // ✅ Email değiştirme
    public FieldUpdateResponse changeEmail(String newEmail) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (userRepository.findByEmail(newEmail).isPresent()) {
            throw new EmailAlreadyUsedException("This email is already in use");
        }

        user.setEmail(newEmail);
        userRepository.save(user);

        return new FieldUpdateResponse("email", newEmail);
    }

    // ✅ Name & Surname değiştirme
    public List<FieldUpdateResponse> changeNameSurname(String newName, String newSurname) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        user.setName(newName);
        user.setSurname(newSurname);
        userRepository.save(user);

        return List.of(
                new FieldUpdateResponse("name", newName),
                new FieldUpdateResponse("surname", newSurname)
        );
    }

    // ✅ Nickname değiştirme
    public FieldUpdateResponse changeNickname(String newNickname) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (newNickname.equals(user.getNickname())) return new FieldUpdateResponse("nickname", newNickname);

        if (userRepository.findByNickname(newNickname).isPresent()) {
            throw new NicknameAlreadyUsedException("This nickname is already in use");
        }

        user.setNickname(newNickname);
        userRepository.save(user);

        return new FieldUpdateResponse("nickname", newNickname);
    }
}
