package com.serdar.personal.model.dto;

import com.serdar.personal.model.Role;

public record UserDTO(
        Long    id,
        String  email,
        String  name,
        String  surname,
        boolean activated,
        Role role,
        String nickname,
        String profileImageUrl
) {}
