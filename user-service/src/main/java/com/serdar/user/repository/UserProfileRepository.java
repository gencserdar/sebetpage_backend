package com.serdar.user.repository;

import com.serdar.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
    Optional<UserProfile> findByEmail(String email);
    Optional<UserProfile> findByNickname(String nickname);
    boolean existsByNickname(String nickname);
    boolean existsByEmail(String email);

    @Query("""
           SELECT u FROM UserProfile u
           WHERE lower(u.name) LIKE lower(concat('%', :kw, '%'))
              OR lower(u.surname) LIKE lower(concat('%', :kw, '%'))
              OR lower(u.nickname) LIKE lower(concat('%', :kw, '%'))
           """)
    List<UserProfile> search(@Param("kw") String keyword);
}
