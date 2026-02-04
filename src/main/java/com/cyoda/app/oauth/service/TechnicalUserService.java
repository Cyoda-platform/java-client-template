package com.cyoda.app.oauth.service;

import com.cyoda.app.oauth.entity.TechnicalUser;
import com.cyoda.app.oauth.repository.TechnicalUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service for managing TechnicalUser (service account) operations.
 * Handles credential validation, account creation, and scope management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TechnicalUserService {

    private final TechnicalUserRepository technicalUserRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Create a new technical user with hashed client secret.
     *
     * @param clientId the client identifier
     * @param plainSecret the plain text client secret (will be hashed)
     * @param scopes the set of scopes for this client
     * @param ownerCustomerId the UUID of the owner customer
     * @param expiresAt optional expiration time
     * @return the created TechnicalUser
     */
    @Transactional
    public TechnicalUser createTechnicalUser(
            String clientId,
            String plainSecret,
            Set<String> scopes,
            UUID ownerCustomerId,
            Instant expiresAt) {

        if (technicalUserRepository.existsByClientId(clientId)) {
            throw new IllegalArgumentException("Client ID already exists: " + clientId);
        }

        String hashedSecret = passwordEncoder.encode(plainSecret);

        TechnicalUser technicalUser = TechnicalUser.builder()
                .clientId(clientId)
                .clientSecretHash(hashedSecret)
                .scopes(scopes != null ? scopes : Set.of())
                .ownerCustomerId(ownerCustomerId)
                .expiresAt(expiresAt)
                .revoked(false)
                .build();

        TechnicalUser saved = technicalUserRepository.save(technicalUser);
        log.info("Created technical user with clientId: {}", clientId);
        return saved;
    }

    /**
     * Validate client credentials (client ID and secret).
     *
     * @param clientId the client identifier
     * @param plainSecret the plain text client secret
     * @return Optional containing the TechnicalUser if credentials are valid
     */
    @Transactional(readOnly = true)
    public Optional<TechnicalUser> validateCredentials(String clientId, String plainSecret) {
        Optional<TechnicalUser> technicalUser = technicalUserRepository.findValidByClientId(clientId);

        if (technicalUser.isEmpty()) {
            log.warn("Invalid client ID or inactive account: {}", clientId);
            return Optional.empty();
        }

        TechnicalUser user = technicalUser.get();
        if (!passwordEncoder.matches(plainSecret, user.getClientSecretHash())) {
            log.warn("Invalid client secret for clientId: {}", clientId);
            return Optional.empty();
        }

        return Optional.of(user);
    }

    /**
     * Get scopes for a technical user.
     *
     * @param clientId the client identifier
     * @return Set of scopes, or empty set if user not found
     */
    @Transactional(readOnly = true)
    public Set<String> getScopes(String clientId) {
        return technicalUserRepository.findByClientId(clientId)
                .map(TechnicalUser::getScopes)
                .orElse(Set.of());
    }

    /**
     * Revoke a technical user account.
     *
     * @param clientId the client identifier
     */
    @Transactional
    public void revokeTechnicalUser(String clientId) {
        technicalUserRepository.findByClientId(clientId).ifPresent(user -> {
            user.setRevoked(true);
            technicalUserRepository.save(user);
            log.info("Revoked technical user: {}", clientId);
        });
    }
}

