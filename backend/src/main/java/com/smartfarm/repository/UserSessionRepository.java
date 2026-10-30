package com.smartfarm.repository;

import com.smartfarm.domain.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from UserSession s where s.tokenHash = :tokenHash")
    int deleteByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from UserSession s where s.user.id = :userId")
    int deleteByUserId(Long userId);

    @Modifying
    @Query("delete from UserSession s where s.expiresAt < :now")
    int deleteExpired(Instant now);
}
