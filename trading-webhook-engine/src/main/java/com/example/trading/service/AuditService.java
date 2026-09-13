package com.example.trading.service;

import com.example.trading.entity.AuditEventEntity;
import com.example.trading.enums.AuditEventType;
import com.example.trading.repository.AuditEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records every important event in the {@code audit_event} table.
 *
 * <p>{@code details} must NEVER contain credentials, tokens, or secrets -
 * every call site in this codebase passes only symbol/quantity/reason-code
 * style text. Each write runs in its own new transaction
 * ({@code REQUIRES_NEW}) so an audit record is never rolled back just
 * because the surrounding business transaction failed - the failure itself
 * is exactly what we need on record.
 */
@Slf4j
@Service
public class AuditService {

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEventType eventType, String signalId, String orderReferenceId, String details) {
        AuditEventEntity entity = new AuditEventEntity();
        entity.setEventType(eventType);
        entity.setSignalId(signalId);
        entity.setOrderReferenceId(orderReferenceId);
        entity.setDetails(details);
        repository.save(entity);
        log.info("AUDIT type={} signalId={} orderRef={} details={}", eventType, signalId, orderReferenceId, details);
    }
}
