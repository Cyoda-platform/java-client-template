package com.cyoda.app.oauth.repository;

import com.cyoda.app.oauth.entity.TechnicalUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA Repository for TechnicalUser entity.
 * Provides database access methods for OAuth2 client credentials.
 */
@Repository
public interface TechnicalUserRepository extends JpaRepository<TechnicalUser, UUID> {

    /**
     * Find a technical user by client ID.
     * Used during token issuance to validate credentials.
     *
     * @param clientId the client identifier
     * @return Optional containing the TechnicalUser if found
     */
    Optional<TechnicalUser> findByClientId(String clientId);

    /**
     * Find a non-revoked, non-expired technical user by client ID.
     * This is the primary method for token issuance validation.
     *
     * @param clientId the client identifier
     * @return Optional containing the valid TechnicalUser if found and active
     */
    @Query("SELECT t FROM TechnicalUser t WHERE t.clientId = :clientId AND t.revoked = false " +
           "AND (t.expiresAt IS NULL OR t.expiresAt > CURRENT_TIMESTAMP)")
    Optional<TechnicalUser> findValidByClientId(@Param("clientId") String clientId);

    /**
     * Check if a client ID already exists.
     *
     * @param clientId the client identifier
     * @return true if the client ID exists, false otherwise
     */
    boolean existsByClientId(String clientId);
}

