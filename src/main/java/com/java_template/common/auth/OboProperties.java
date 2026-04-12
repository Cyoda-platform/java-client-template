package com.java_template.common.auth;

// ABOUTME: Configuration properties for Cyoda OBO (On-Behalf-Of) token exchange.
// The encryption key can be supplied via the encryption-key property (env var) or read
// from a file at the path given by encryption-key-file. The env var takes precedence.
// When neither source provides a key, isEnabled() returns false and any user-triggered
// Cyoda call will throw OboTokenException (fail-fast).

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@ConfigurationProperties(prefix = OboProperties.CONFIG_PREFIX)
public class OboProperties {

    private static final Logger logger = LoggerFactory.getLogger(OboProperties.class);

    public static final String CONFIG_PREFIX = "app.obo";

    private String encryptionKey = "";
    private String encryptionKeyFile = "/app/obo-encryption-key.txt";
    private String adminClientId = "";
    private String adminClientSecret = "";
    private String keyId = "bloc-portal-key-001";
    private String issuer = "bloc-portal-local";
    private int subjectTokenTtlSeconds = 300;
    private String rolesClaimName = "roles";
    private int validityDays = 90;
    private int rotationWarningDays = 14;
    private int rotationGracePeriodSeconds = 3600;

    /**
     * Resolves the encryption key from the env-var property first, falling back to the
     * file path. Returns null if neither source provides a key.
     */
    public String resolveEncryptionKey() {
        if (encryptionKey != null && !encryptionKey.isBlank()) {
            return encryptionKey;
        }
        if (encryptionKeyFile != null && !encryptionKeyFile.isBlank()) {
            Path path = Path.of(encryptionKeyFile);
            if (Files.isReadable(path)) {
                try {
                    String key = Files.readString(path).trim();
                    if (!key.isBlank()) {
                        logger.info("OBO encryption key loaded from file: {}", encryptionKeyFile);
                        return key;
                    }
                } catch (IOException e) {
                    logger.warn("Failed to read OBO encryption key file {}: {}", encryptionKeyFile, e.getMessage());
                }
            }
        }
        return null;
    }

    public boolean isEnabled() {
        return resolveEncryptionKey() != null;
    }

    public String getEncryptionKey() { return encryptionKey; }
    public void setEncryptionKey(String encryptionKey) { this.encryptionKey = encryptionKey; }

    public String getEncryptionKeyFile() { return encryptionKeyFile; }
    public void setEncryptionKeyFile(String encryptionKeyFile) { this.encryptionKeyFile = encryptionKeyFile; }

    public String getAdminClientId() { return adminClientId; }
    public void setAdminClientId(String adminClientId) { this.adminClientId = adminClientId; }

    public String getAdminClientSecret() { return adminClientSecret; }
    public void setAdminClientSecret(String adminClientSecret) { this.adminClientSecret = adminClientSecret; }

    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }

    public int getSubjectTokenTtlSeconds() { return subjectTokenTtlSeconds; }
    public void setSubjectTokenTtlSeconds(int s) { this.subjectTokenTtlSeconds = s; }

    public String getRolesClaimName() { return rolesClaimName; }
    public void setRolesClaimName(String rolesClaimName) { this.rolesClaimName = rolesClaimName; }

    public int getValidityDays() { return validityDays; }
    public void setValidityDays(int validityDays) { this.validityDays = validityDays; }

    public int getRotationWarningDays() { return rotationWarningDays; }
    public void setRotationWarningDays(int rotationWarningDays) { this.rotationWarningDays = rotationWarningDays; }

    public int getRotationGracePeriodSeconds() { return rotationGracePeriodSeconds; }
    public void setRotationGracePeriodSeconds(int s) { this.rotationGracePeriodSeconds = s; }
}
