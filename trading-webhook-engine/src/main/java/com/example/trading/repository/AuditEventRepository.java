package com.example.trading.repository;

import com.example.trading.entity.AuditEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, Long>,
        JpaSpecificationExecutor<AuditEventEntity> {

    List<AuditEventEntity> findBySignalIdOrderByCreatedAtAsc(String signalId);

    List<AuditEventEntity> findByOrderReferenceIdOrderByCreatedAtAsc(String orderReferenceId);
}
