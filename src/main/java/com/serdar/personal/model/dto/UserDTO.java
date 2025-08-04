package com.serdar.personal.model.dto;

import com.serdar.personal.model.Role;

public record UserDTO(
        Long    id,
        String  email,
        String  fullName,
        boolean activated,
        Role role
) {}
