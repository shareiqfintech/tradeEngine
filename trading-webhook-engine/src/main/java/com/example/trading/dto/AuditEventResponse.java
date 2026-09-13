package com.example.trading.dto;

import com.example.trading.entity.AuditEventEntity;
import com.example.trading.enums.AuditEventType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/** GET /api/trading/audit row - mirrors AuditEventEntity exactly (frontend's AuditEvent type). */
@Data
@Builder
public class AuditEventResponse {
    private Long id;
    private String signalId;
    private String orderReferenceId;
    private AuditEventType eventType;
    private String details;
    private Instant createdAt;

    public static AuditEventResponse from(AuditEventEntity entity) {
        return AuditEventResponse.builder()
                .id(entity.getId())
                .signalId(entity.getSignalId())
                .orderReferenceId(entity.getOrderReferenceId())
                .eventType(entity.getEventType())
                .details(entity.getDetails())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
