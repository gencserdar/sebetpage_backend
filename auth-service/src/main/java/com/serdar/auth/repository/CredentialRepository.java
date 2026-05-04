package com.serdar.auth.repository;

import com.serdar.auth.entity.Credential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CredentialRepository extends JpaRepository<Credential, Long> {
    Optional<Credential> findByEmail(String email);
    Optional<Credential> findByNickname(String nickname);
    Optional<Credential> findByActivationCode(String code);
    Optional<Credential> findByResetCode(String code);
    Optional<Credential> findByRefreshToken(String token);
    boolean existsByEmail(String email);
    boolean existsByNickname(String nickname);
}
