package com.smartfarm.repository;

import com.smartfarm.domain.Invite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InviteRepository extends JpaRepository<Invite, Long> {

    Optional<Invite> findByTokenHash(String tokenHash);

    List<Invite> findByUser_IdAndUsedAtIsNull(Long userId);
}
