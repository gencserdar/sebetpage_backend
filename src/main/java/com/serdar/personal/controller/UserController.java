package com.serdar.personal.controller;

import com.serdar.personal.model.User;
import com.serdar.personal.model.dto.UserDTO;
import com.serdar.personal.repository.UserRepository;
import com.serdar.personal.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public UserDTO me() {
        return userService.getCurrentUser();
    }


    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    @GetMapping("/profile/{nickname}")
    public ResponseEntity<UserDTO> getByNickname(@PathVariable String nickname) {
        UserDTO user = userService.getByNickname(nickname);
        return ResponseEntity.ok(user);
    }

}
