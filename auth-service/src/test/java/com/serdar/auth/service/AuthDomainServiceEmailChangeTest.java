package com.serdar.auth.service;

import com.serdar.auth.entity.Credential;
import com.serdar.auth.entity.Role;
import com.serdar.auth.repository.CredentialRepository;
import com.serdar.auth.repository.SessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthDomainServiceEmailChangeTest {

    @Mock private CredentialRepository repo;
    @Mock private SessionRepository sessions;
    @Mock private JwtIssuer jwt;
    @Mock private EmailService email;
    @Mock private RefreshTokenHasher refreshHasher;
    @Mock private org.springframework.security.crypto.password.PasswordEncoder encoder;

    @InjectMocks private AuthDomainService service;

    @Test
    void confirmEmailChangeRevokesSessions() {
        ReflectionTestUtils.setField(service, "accountChangeCodeMaxAttempts", 5);

        Credential c = Credential.builder()
                .id(9L)
                .email("old@example.com")
                .nickname("user9")
                .password("hash")
                .role(Role.USER)
                .activated(true)
                .pendingEmailCode("123456")
                .pendingEmailNew("new@example.com")
                .pendingEmailExpiresAt(LocalDateTime.now().plusMinutes(10))
                .pendingEmailAttempts(0)
                .build();

        when(repo.findById(9L)).thenReturn(Optional.of(c));
        when(repo.existsByEmail("new@example.com")).thenReturn(false);
        service.confirmEmailChange(9L, "123456");

        verify(sessions).deleteAllByUserId(9L);
    }
}
