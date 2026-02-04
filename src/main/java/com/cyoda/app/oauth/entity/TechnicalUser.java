package com.cyoda.app.oauth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * JPA Entity for TechnicalUser (Service Account)
 * Represents OAuth2 client credentials for machine-to-machine authentication.
 */
@Entity
@Table(name = "technical_users", indexes = {
    @Index(name = "idx_client_id", columnList = "client_id", unique = true),
    @Index(name = "idx_owner_customer_id", columnList = "owner_customer_id"),
    @Index(name = "idx_revoked", columnList = "revoked")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechnicalUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "client_id", nullable = false, unique = true, length = 255)
    private String clientId;

    @Column(name = "client_secret_hash", nullable = false, length = 255)
    private String clientSecretHash;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "technical_user_scopes", joinColumns = @JoinColumn(name = "technical_user_id"))
    @Column(name = "scope")
    private Set<String> scopes = new HashSet<>();

    @Column(name = "owner_customer_id", nullable = false)
    private UUID ownerCustomerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    private Boolean revoked = false;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (revoked == null) {
            revoked = false;
        }
    }

    /**
     * Check if this technical user is valid for token issuance
     */
    public boolean isValid() {
        if (revoked) {
            return false;
        }
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
            return false;
        }
        return clientId != null && !clientId.isBlank() && clientSecretHash != null;
    }
}

