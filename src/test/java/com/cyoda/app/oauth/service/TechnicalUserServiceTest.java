package com.cyoda.app.oauth.service;

import com.cyoda.app.oauth.entity.TechnicalUser;
import com.cyoda.app.oauth.repository.TechnicalUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TechnicalUserServiceTest {

    @Mock
    private TechnicalUserRepository technicalUserRepository;

    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private TechnicalUserService technicalUserService;

    private UUID testCustomerId;
    private String testClientId;
    private String testSecret;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        technicalUserService = new TechnicalUserService(technicalUserRepository, passwordEncoder);
        testCustomerId = UUID.randomUUID();
        testClientId = "test-client-001";
        testSecret = "test-secret-12345";
    }

    @Test
    void testCreateTechnicalUser() {
        when(technicalUserRepository.existsByClientId(testClientId)).thenReturn(false);
        when(technicalUserRepository.save(any(TechnicalUser.class))).thenAnswer(invocation -> {
            TechnicalUser user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        Set<String> scopes = Set.of("customer:read", "customer:write");
        TechnicalUser result = technicalUserService.createTechnicalUser(
                testClientId, testSecret, scopes, testCustomerId, null);

        assertNotNull(result);
        assertEquals(testClientId, result.getClientId());
        assertEquals(scopes, result.getScopes());
        assertEquals(testCustomerId, result.getOwnerCustomerId());
        assertFalse(result.getRevoked());
        verify(technicalUserRepository).save(any(TechnicalUser.class));
    }

    @Test
    void testCreateTechnicalUserDuplicateClientId() {
        when(technicalUserRepository.existsByClientId(testClientId)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
                technicalUserService.createTechnicalUser(
                        testClientId, testSecret, Set.of(), testCustomerId, null));
    }

    @Test
    void testValidateCredentialsSuccess() {
        String hashedSecret = passwordEncoder.encode(testSecret);
        TechnicalUser technicalUser = TechnicalUser.builder()
                .id(UUID.randomUUID())
                .clientId(testClientId)
                .clientSecretHash(hashedSecret)
                .scopes(Set.of("customer:read"))
                .ownerCustomerId(testCustomerId)
                .revoked(false)
                .build();

        when(technicalUserRepository.findValidByClientId(testClientId))
                .thenReturn(Optional.of(technicalUser));

        Optional<TechnicalUser> result = technicalUserService.validateCredentials(testClientId, testSecret);

        assertTrue(result.isPresent());
        assertEquals(testClientId, result.get().getClientId());
    }

    @Test
    void testValidateCredentialsInvalidSecret() {
        String hashedSecret = passwordEncoder.encode(testSecret);
        TechnicalUser technicalUser = TechnicalUser.builder()
                .id(UUID.randomUUID())
                .clientId(testClientId)
                .clientSecretHash(hashedSecret)
                .scopes(Set.of())
                .ownerCustomerId(testCustomerId)
                .revoked(false)
                .build();

        when(technicalUserRepository.findValidByClientId(testClientId))
                .thenReturn(Optional.of(technicalUser));

        Optional<TechnicalUser> result = technicalUserService.validateCredentials(testClientId, "wrong-secret");

        assertTrue(result.isEmpty());
    }

    @Test
    void testValidateCredentialsClientNotFound() {
        when(technicalUserRepository.findValidByClientId(testClientId))
                .thenReturn(Optional.empty());

        Optional<TechnicalUser> result = technicalUserService.validateCredentials(testClientId, testSecret);

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetScopes() {
        Set<String> scopes = Set.of("customer:read", "customer:write");
        TechnicalUser technicalUser = TechnicalUser.builder()
                .id(UUID.randomUUID())
                .clientId(testClientId)
                .clientSecretHash("hash")
                .scopes(scopes)
                .ownerCustomerId(testCustomerId)
                .revoked(false)
                .build();

        when(technicalUserRepository.findByClientId(testClientId))
                .thenReturn(Optional.of(technicalUser));

        Set<String> result = technicalUserService.getScopes(testClientId);

        assertEquals(scopes, result);
    }

    @Test
    void testRevokeTechnicalUser() {
        TechnicalUser technicalUser = TechnicalUser.builder()
                .id(UUID.randomUUID())
                .clientId(testClientId)
                .clientSecretHash("hash")
                .scopes(Set.of())
                .ownerCustomerId(testCustomerId)
                .revoked(false)
                .build();

        when(technicalUserRepository.findByClientId(testClientId))
                .thenReturn(Optional.of(technicalUser));
        when(technicalUserRepository.save(any(TechnicalUser.class)))
                .thenReturn(technicalUser);

        technicalUserService.revokeTechnicalUser(testClientId);

        verify(technicalUserRepository).save(any(TechnicalUser.class));
    }
}

