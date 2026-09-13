package com.example.trading.repository;

import com.example.trading.entity.PositionSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionSnapshotRepository extends JpaRepository<PositionSnapshotEntity, Long> {
}
