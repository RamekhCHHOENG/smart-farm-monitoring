package com.smartfarm.repository;

import com.smartfarm.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FarmRepository extends JpaRepository<Farm, Long> {

    Optional<Farm> findFirstByOrderByIdAsc();

    java.util.List<Farm> findAllByOrderByNameAsc();
}
