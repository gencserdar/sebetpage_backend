package com.serdar.gateway.controller;

import com.serdar.gateway.dto.Dtos;
import com.serdar.proto.auth.Credentials;
import com.serdar.proto.auth.Role;
import com.serdar.proto.user.UserProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserControllerProfileTest {

    @Test
    void publicProfileOmitsEmail() {
        Credentials c = Credentials.newBuilder()
                .setId(2L)
                .setEmail("secret@example.com")
                .setActivated(true)
                .setRole(Role.USER)
                .build();
        UserProfile p = UserProfile.newBuilder()
                .setId(2L)
                .setEmail("secret@example.com")
                .setNickname("alice")
                .build();

        Dtos.UserDTO dto = UserController.publicProfile(c, p);

        assertNull(dto.getEmail());
    }

    @Test
    void frozenPublicProfileExposesNicknameOnly() {
        Credentials c = Credentials.newBuilder()
                .setId(3L)
                .setActivated(true)
                .setFrozen(true)
                .setRole(Role.USER)
                .build();
        UserProfile p = UserProfile.newBuilder()
                .setId(3L)
                .setNickname("frozen_user")
                .setName("Secret")
                .setSurname("Person")
                .setProfileImageUrl("https://example.com/photo.jpg")
                .build();

        Dtos.UserDTO dto = UserController.frozenPublicProfile(c, p);

        assertEquals("frozen_user", dto.getNickname());
        assertEquals(Boolean.TRUE, dto.getFrozen());
        assertNull(dto.getName());
        assertNull(dto.getSurname());
        assertNull(dto.getProfileImageUrl());
        assertNull(dto.getEmail());
    }
}
