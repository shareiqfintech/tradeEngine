package com.example.trading.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A user's Groww API credentials, one row per user (enforced by a unique
 * constraint on {@code user_id} - see V3 migration). {@code apiKeyEncrypted}
 * and {@code totpSecretEncrypted} are AES-GCM ciphertext (see
 * {@link com.example.trading.security.CredentialEncryptionService}) - the
 * plaintext values are never persisted, logged, or returned over HTTP.
 */
@Entity
@Table(name = "groww_configuration")
@Getter
@Setter
@NoArgsConstructor
public class GrowwConfigurationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "api_key_encrypted", length = 2000)
    private String apiKeyEncrypted;

    @Column(name = "totp_secret_encrypted", length = 2000)
    private String totpSecretEncrypted;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    /**
     * True only after an explicit, successful Test Connection call. Changing
     * {@code apiKeyEncrypted}/{@code totpSecretEncrypted}, or a failed
     * re-authentication attempt, flips this back to false - it is never
     * set true as a side effect of anything else.
     */
    @Column(name = "connected", nullable = false)
    private boolean connected = false;

    @Column(name = "last_connected_at")
    private Instant lastConnectedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public static GrowwConfigurationEntity forUser(Long userId) {
        GrowwConfigurationEntity entity = new GrowwConfigurationEntity();
        entity.setUserId(userId);
        return entity;
    }
}
