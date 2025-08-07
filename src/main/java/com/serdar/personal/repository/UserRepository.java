package com.serdar.personal.repository;

import com.serdar.personal.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByActivationCode(String code);
    Optional<User> findByRefreshToken(String refreshToken);
    Optional<User> findByResetCode(String resetCode);

    Optional<User> findByNickname(String nickname);
    List<User> findByNameContainingIgnoreCaseOrSurnameContainingIgnoreCaseOrNicknameContainingIgnoreCase(
            String name, String surname, String nickname);
}