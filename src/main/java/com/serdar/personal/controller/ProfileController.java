package com.serdar.personal.controller;

import com.serdar.personal.model.dto.ChangePasswordRequest;
import com.serdar.personal.model.dto.FieldUpdateResponse;
import com.serdar.personal.service.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    // ✅ Profil fotoğrafı yükleme
    @PostMapping("/upload-photo")
    public ResponseEntity<FieldUpdateResponse> uploadPhoto(@RequestParam("file") MultipartFile file) {
        FieldUpdateResponse response = profileService.uploadProfilePhoto(file);
        return ResponseEntity.ok(response);
    }

    // ✅ Şifre değiştirme
    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(@RequestBody ChangePasswordRequest request) {
        profileService.changePassword(request);
        return ResponseEntity.ok("Password changed successfully");
    }

    // ✅ Email değiştirme
    @PutMapping("/change-email")
    public ResponseEntity<FieldUpdateResponse> changeEmail(@RequestParam String newEmail) {
        FieldUpdateResponse response = profileService.changeEmail(newEmail);
        return ResponseEntity.ok(response);
    }

    // ✅ Name & Surname değiştirme
    @PutMapping("/change-name-surname")
    public ResponseEntity<List<FieldUpdateResponse>> changeNameSurname(
            @RequestParam String newName,
            @RequestParam String newSurname) {
        List<FieldUpdateResponse> response = profileService.changeNameSurname(newName, newSurname);
        return ResponseEntity.ok(response);
    }

    // ✅ Nickname değiştirme
    @PutMapping("/change-nickname")
    public ResponseEntity<FieldUpdateResponse> changeNickname(@RequestParam String newNickname) {
        FieldUpdateResponse response = profileService.changeNickname(newNickname);
        return ResponseEntity.ok(response);
    }
}
