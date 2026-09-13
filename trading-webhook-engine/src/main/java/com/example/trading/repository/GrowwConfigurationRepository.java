package com.example.trading.repository;

import com.example.trading.entity.GrowwConfigurationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GrowwConfigurationRepository extends JpaRepository<GrowwConfigurationEntity, Long> {
    Optional<GrowwConfigurationEntity> findByUserId(Long userId);

    /** Used by {@code GrowwUserResolver} to find every account the webhook-triggered automated engine (which has no logged-in session) should trade with - never just one of them. */
    List<GrowwConfigurationEntity> findByConnectedTrueOrderByLastConnectedAtDesc();
}
