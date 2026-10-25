package com.smartfarm.repository;

import com.smartfarm.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    List<AppUser> findAllByOrderByIdAsc();

    Optional<AppUser> findByUsername(String username);

    List<AppUser> findByFarm_IdOrderByIdAsc(Long farmId);
    List<AppUser> findByCompanyRoleIsNotNullOrderByNameAsc();
    boolean existsByCompanyRole(String companyRole);
}
