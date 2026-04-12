package com.java_template.common.auth;

// ABOUTME: Manages the OBO signing key lifecycle: entity-based encrypted key storage,
// idempotent Cyoda registration, and proactive rotation with a configurable grace period.

import com.java_template.common.config.Config;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SslUtils;
import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.annotation.PostConstruct;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OboKeyRegistrationService {

    private static final Logger logger = LoggerFactory.getLogger(OboKeyRegistrationService.class);

    private final OboProperties oboProperties;
    private final SubjectTokenSigner subjectTokenSigner;
    private final Authentication authentication;
    private final EntityService entityService;
    private final Config config;
    private final RestClient restClient;

    public OboKeyRegistrationService(OboProperties oboProperties,
                                     SubjectTokenSigner subjectTokenSigner,
                                     Authentication authentication,
                                     EntityService entityService,
                                     Config config) {
        this.oboProperties = oboProperties;
        this.subjectTokenSigner = subjectTokenSigner;
        this.authentication = authentication;
        this.entityService = entityService;
        this.config = config;
        this.restClient = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(SslUtils.createHttpClient(config)))
                .build();
    }

    @PostConstruct
    public void onStartup() {
        if (!oboProperties.isEnabled()) {
            logger.warn("OBO is not configured — no encryption key found via property or file. "
                    + "Set app.obo.encryption-key or provide a key file at app.obo.encryption-key-file. "
                    + "Skipping key setup.");
            return;
        }
        checkAndEnsureKeyRegistered();
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void scheduledCheck() {
        if (!oboProperties.isEnabled()) return;
        checkAndEnsureKeyRegistered();
    }

    private void checkAndEnsureKeyRegistered() {
        // Ensure all Cyoda calls (gRPC and HTTP) within this method use the M2M
        // service-account token for app.config.cyoda-client-id. Startup and scheduler
        // threads carry an empty SecurityContext by default, but we make it explicit
        // so that OboAwareAuthentication always takes the M2M path rather than
        // accidentally entering the OBO path if anything was placed on the thread.
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContextHolder.clearContext();
        try {
            String resolvedKey = oboProperties.resolveEncryptionKey();
            if (resolvedKey == null) {
                throw new OboTokenException(
                        "OBO encryption key could not be resolved from property or file. "
                        + "Set app.obo.encryption-key or ensure a readable file at app.obo.encryption-key-file.");
            }
            SecretKey aesKey = AesGcmEncryption.decodeKey(resolvedKey);

            String caasOrgId = fetchCaasOrgId();
            subjectTokenSigner.setActiveCaasOrgId(caasOrgId);
            logger.info("caas_org_id fetched from Cyoda: {}", caasOrgId);

            ensureEntityModelRegistered();

            List<EntityWithMetadata<OboSigningKey>> entities =
                    entityService.findAll(OboSigningKey.MODEL_SPEC, OboSigningKey.class).data();

            if (entities == null || entities.isEmpty()) {
                logger.info("No OboSigningKey entity found — bootstrapping new key pair");
                bootstrapNewKey(aesKey);
                return;
            }

            EntityWithMetadata<OboSigningKey> entityWithMetadata = entities.getFirst();
            OboSigningKey signingKey = entityWithMetadata.entity();

            RSAPrivateKey privateKey = tryDecryptOrRebootstrap(entityWithMetadata, signingKey, aesKey);
            subjectTokenSigner.setActiveKey(privateKey);
            logger.info("OBO signing key loaded from Cyoda entity (key ID: {})", signingKey.getKeyId());

            checkCyodaKeyValidity(entityWithMetadata, signingKey, aesKey);

        } catch (OboTokenException e) {
            logger.error("OBO key setup failed: {}", e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error during OBO key setup: {}", e.getMessage(), e);
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    /**
     * Attempts to decrypt the stored private key. If decryption fails (e.g. the
     * encryption key changed due to a new Docker image build or secret rotation),
     * re-bootstraps with a new RSA key pair encrypted under the current key.
     */
    private RSAPrivateKey tryDecryptOrRebootstrap(EntityWithMetadata<OboSigningKey> entityWithMetadata,
                                                   OboSigningKey signingKey, SecretKey aesKey) {
        try {
            return decryptPrivateKey(signingKey.getEncryptedPrivateKey(), aesKey);
        } catch (OboTokenException e) {
            logger.warn("Failed to decrypt existing OBO signing key — encryption key has likely changed "
                    + "(new image build or secret rotation). Re-bootstrapping RSA key pair with grace period.");
            return rebootstrapKey(entityWithMetadata, aesKey);
        }
    }

    private RSAPrivateKey rebootstrapKey(EntityWithMetadata<OboSigningKey> entityWithMetadata, SecretKey aesKey) {
        KeyPair keyPair = generateRsaKeyPair();
        RSAPrivateKey newPrivateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAPublicKey newPublicKey = (RSAPublicKey) keyPair.getPublic();

        String newPublicKeyDer = Base64.getEncoder().encodeToString(newPublicKey.getEncoded());
        int graceSec = oboProperties.getRotationGracePeriodSeconds();
        String newExpiresAt = registerPublicKeyWithCyoda(newPublicKey, true, graceSec);

        String encryptedNewPrivateKey = AesGcmEncryption.encrypt(newPrivateKey.getEncoded(), aesKey);

        OboSigningKey updated = entityWithMetadata.entity();
        updated.setEncryptedPrivateKey(encryptedNewPrivateKey);
        updated.setPublicKeyDer(newPublicKeyDer);
        updated.setCyodaKeyExpiresAt(newExpiresAt);
        updated.setLastRotatedAt(Instant.now().toString());

        entityService.update(entityWithMetadata.metadata().getId(), updated, null);
        logger.info("OBO signing key re-bootstrapped after encryption key change (grace period: {}s)", graceSec);

        return newPrivateKey;
    }

    @SuppressWarnings("unchecked")
    private String fetchCaasOrgId() {
        String bearerToken = authentication.getAccessToken().getTokenValue();
        Map<String, Object> response = restClient.get()
                .uri(config.getCyodaApiUrl() + "/account")
                .header("Authorization", "Bearer " + bearerToken)
                .retrieve()
                .body(Map.class);
        try {
            if (response == null) {
                throw new OboTokenException("Cyoda /account returned null response");
            }
            Map<String, Object> userAccountInfo = (Map<String, Object>) response.get("userAccountInfo");
            Map<String, Object> legalEntity = (Map<String, Object>) userAccountInfo.get("legalEntity");
            String orgId = (String) legalEntity.get("id");
            if (orgId == null || orgId.isBlank()) {
                throw new OboTokenException("Cyoda /account returned a blank legalEntity.id");
            }
            return orgId;
        } catch (OboTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new OboTokenException("Failed to extract caas_org_id from Cyoda /account response", e);
        }
    }

    private void ensureEntityModelRegistered() {
        String bearerToken = authentication.getAccessToken().getTokenValue();
        String entityName = OboSigningKey.ENTITY_NAME;
        int version = OboSigningKey.ENTITY_VERSION;

        boolean exists = checkEntityModelExists(bearerToken, entityName, version);
        if (exists) {
            logger.debug("OboSigningKey entity model already exists in Cyoda");
            return;
        }

        logger.info("OboSigningKey entity model not found in Cyoda — bootstrapping");

        String workflowJson = loadWorkflowJsonFromClasspath();
        String workflowBody = "{\"workflows\":" + workflowJson + ",\"importMode\":\"REPLACE\"}";
        restClient.post()
                .uri(config.getCyodaApiUrl() + "/model/{name}/{version}/workflow/import",
                        entityName, version)
                .header("Authorization", "Bearer " + bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(workflowBody)
                .retrieve()
                .toBodilessEntity();
        logger.info("OboSigningKey workflow imported");

        String sampleJson = loadSampleEntityJsonFromClasspath();
        restClient.post()
                .uri(config.getCyodaApiUrl() + "/model/import/JSON/SAMPLE_DATA/{name}/{version}",
                        entityName, version)
                .header("Authorization", "Bearer " + bearerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(sampleJson)
                .retrieve()
                .toBodilessEntity();
        logger.info("OboSigningKey entity model schema created");

        restClient.post()
                .uri(config.getCyodaApiUrl() + "/model/{name}/{version}/changeLevel/STRUCTURAL",
                        entityName, version)
                .header("Authorization", "Bearer " + bearerToken)
                .retrieve()
                .toBodilessEntity();

        restClient.put()
                .uri(config.getCyodaApiUrl() + "/model/{name}/{version}/lock",
                        entityName, version)
                .header("Authorization", "Bearer " + bearerToken)
                .retrieve()
                .toBodilessEntity();

        logger.info("OboSigningKey entity model registered and locked in Cyoda");
    }

    private boolean checkEntityModelExists(String bearerToken, String entityName, int version) {
        try {
            restClient.get()
                    .uri(config.getCyodaApiUrl() + "/model/export/SIMPLE_VIEW/{name}/{version}",
                            entityName, version)
                    .header("Authorization", "Bearer " + bearerToken)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            return false;
        }
    }

    private String loadWorkflowJsonFromClasspath() {
        String path = "/workflow/v1/obosigningkey.json";
        try (var stream = getClass().getResourceAsStream(path)) {
            if (stream == null) {
                throw new OboTokenException("OboSigningKey workflow file not found on classpath: " + path);
            }
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (OboTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new OboTokenException("Failed to load OboSigningKey workflow JSON from classpath", e);
        }
    }

    private String loadSampleEntityJsonFromClasspath() {
        String path = "/entity-schemas/examples/OboSigningKey/obo-signing-key.json";
        try (var stream = getClass().getResourceAsStream(path)) {
            if (stream == null) {
                logger.warn("OboSigningKey sample JSON not found at {}; using inline fallback", path);
                return "{\"key_id\":\"example\",\"encrypted_private_key\":\"ZQ==\","
                        + "\"public_key_der\":\"ZQ==\","
                        + "\"cyoda_key_expires_at\":\"2027-01-01T00:00:00Z\","
                        + "\"last_rotated_at\":\"2026-01-01T00:00:00Z\"}";
            }
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new OboTokenException("Failed to load OboSigningKey sample entity JSON", e);
        }
    }

    private void bootstrapNewKey(SecretKey aesKey) {
        KeyPair keyPair = generateRsaKeyPair();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();

        String encryptedPrivateKey = AesGcmEncryption.encrypt(privateKey.getEncoded(), aesKey);
        String publicKeyDer = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        String expiresAt = registerPublicKeyWithCyoda(publicKey, false, 0);

        OboSigningKey entity = new OboSigningKey();
        entity.setKeyId(oboProperties.getKeyId());
        entity.setEncryptedPrivateKey(encryptedPrivateKey);
        entity.setPublicKeyDer(publicKeyDer);
        entity.setCyodaKeyExpiresAt(expiresAt);
        entity.setLastRotatedAt(Instant.now().toString());

        entityService.create(entity);
        subjectTokenSigner.setActiveKey(privateKey);
        logger.info("OBO key bootstrapped and registered with Cyoda (key ID: {}, expires: {})",
                oboProperties.getKeyId(), expiresAt);
    }

    private void checkCyodaKeyValidity(EntityWithMetadata<OboSigningKey> entityWithMetadata,
                                        OboSigningKey signingKey, SecretKey aesKey) {
        String cyodaKeyExpiresAt = signingKey.getCyodaKeyExpiresAt();
        if (cyodaKeyExpiresAt == null) {
            logger.warn("OboSigningKey entity has no cyodaKeyExpiresAt — treating as expired; rotating");
            rotateKey(entityWithMetadata, aesKey, false);
            return;
        }

        LocalDate expiryDate;
        try {
            expiryDate = Instant.parse(cyodaKeyExpiresAt)
                    .atZone(ZoneOffset.UTC).toLocalDate();
        } catch (Exception e) {
            expiryDate = LocalDate.parse(cyodaKeyExpiresAt, DateTimeFormatter.ISO_LOCAL_DATE);
        }

        long daysRemaining = LocalDate.now(ZoneOffset.UTC).until(expiryDate).getDays();

        if (daysRemaining <= 0) {
            logger.warn("OBO signing key has expired ({}) — rotating immediately", cyodaKeyExpiresAt);
            rotateKey(entityWithMetadata, aesKey, false);
        } else if (daysRemaining <= oboProperties.getRotationWarningDays()) {
            logger.warn("OBO signing key expires in {} day(s) — auto-rotating with grace period", daysRemaining);
            rotateKey(entityWithMetadata, aesKey, true);
        } else {
            logger.debug("OBO signing key is healthy; expires in {} day(s)", daysRemaining);
        }
    }

    private void rotateKey(EntityWithMetadata<OboSigningKey> entityWithMetadata,
                           SecretKey aesKey, boolean withGracePeriod) {
        try {
            KeyPair keyPair = generateRsaKeyPair();
            RSAPrivateKey newPrivateKey = (RSAPrivateKey) keyPair.getPrivate();
            RSAPublicKey newPublicKey = (RSAPublicKey) keyPair.getPublic();

            String newPublicKeyDer = Base64.getEncoder().encodeToString(newPublicKey.getEncoded());
            int graceSec = withGracePeriod ? oboProperties.getRotationGracePeriodSeconds() : 0;
            String newExpiresAt = registerPublicKeyWithCyoda(newPublicKey, true, graceSec);

            String encryptedNewPrivateKey = AesGcmEncryption.encrypt(newPrivateKey.getEncoded(), aesKey);

            OboSigningKey updated = entityWithMetadata.entity();
            updated.setEncryptedPrivateKey(encryptedNewPrivateKey);
            updated.setPublicKeyDer(newPublicKeyDer);
            updated.setCyodaKeyExpiresAt(newExpiresAt);
            updated.setLastRotatedAt(Instant.now().toString());

            entityService.update(entityWithMetadata.metadata().getId(), updated, null);
            subjectTokenSigner.setActiveKey(newPrivateKey);

            logger.info("OBO signing key rotated successfully (new expiry: {}, grace period: {}s)",
                    newExpiresAt, graceSec);

        } catch (Exception e) {
            logger.error("OBO key rotation failed: {}", e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private String fetchAdminToken() {
        String credentials = Base64.getEncoder().encodeToString(
                (oboProperties.getAdminClientId() + ":" + oboProperties.getAdminClientSecret())
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Map<String, Object> response = restClient.post()
                .uri(config.getCyodaApiUrl() + "/oauth/token")
                .header("Authorization", "Basic " + credentials)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials")
                .retrieve()
                .body(Map.class);
        if (response == null || response.get("access_token") == null) {
            throw new OboTokenException("Admin token exchange returned no access_token");
        }
        return (String) response.get("access_token");
    }

    @SuppressWarnings("unchecked")
    private String registerPublicKeyWithCyoda(RSAPublicKey publicKey,
                                               boolean invalidatePrevious, int gracePeriodSeconds) {
        String bearerToken = fetchAdminToken();

        RSAKey jwk = new RSAKey.Builder(publicKey)
                .keyID(oboProperties.getKeyId())
                .algorithm(new Algorithm("RS256"))
                .keyUse(KeyUse.SIGNATURE)
                .build();

        String validTo = Instant.now()
                .plus(Duration.ofDays(oboProperties.getValidityDays()))
                .atZone(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("keyId", oboProperties.getKeyId());
        body.put("jwk", jwk.toJSONObject());
        body.put("audience", "human");
        body.put("issuers", List.of(oboProperties.getIssuer()));
        body.put("validTo", validTo);
        if (invalidatePrevious) {
            body.put("invalidatePrevious", true);
            body.put("invalidateGracePeriodSec", gracePeriodSeconds);
        }

        try {
            Map<String, Object> response = restClient.post()
                    .uri(config.getCyodaApiUrl() + "/oauth/keys/trusted")
                    .header("Authorization", "Bearer " + bearerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            String responseValidTo = response != null ? (String) response.get("validTo") : null;
            if (responseValidTo == null) {
                responseValidTo = validTo;
            }
            return responseValidTo;

        } catch (HttpClientErrorException.Conflict e) {
            throw new OboTokenException(
                    "Trusted key '" + oboProperties.getKeyId() + "' is owned by a different tenant in Cyoda. "
                    + "Each tenant must use a unique keyId. Check app.obo.key-id configuration. "
                    + "Detail: " + e.getResponseBodyAsString());
        }
    }

    private RSAPrivateKey decryptPrivateKey(String encryptedPrivateKey, SecretKey aesKey) {
        try {
            byte[] pkcs8Bytes = AesGcmEncryption.decrypt(encryptedPrivateKey, aesKey);
            return (RSAPrivateKey) java.security.KeyFactory.getInstance("RSA")
                    .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(pkcs8Bytes));
        } catch (OboTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new OboTokenException("Failed to decrypt OBO private key from entity", e);
        }
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            return kpg.generateKeyPair();
        } catch (Exception e) {
            throw new OboTokenException("Failed to generate RSA key pair", e);
        }
    }
}
