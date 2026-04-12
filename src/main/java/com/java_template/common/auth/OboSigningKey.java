package com.java_template.common.auth;

// ABOUTME: Cyoda entity holding the OBO signing key state, including the AES-encrypted RSA private key.
// Only one instance per deployment; managed entirely by OboKeyRegistrationService.

import com.fasterxml.jackson.annotation.JsonProperty;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

public class OboSigningKey implements CyodaEntity {

    public static final String ENTITY_NAME = "OboSigningKey";
    public static final Integer ENTITY_VERSION = 1;
    public static final ModelSpec MODEL_SPEC = new ModelSpec()
            .withName(ENTITY_NAME)
            .withVersion(ENTITY_VERSION);

    @JsonProperty("key_id")
    private String keyId;

    @JsonProperty("encrypted_private_key")
    private String encryptedPrivateKey;

    @JsonProperty("public_key_der")
    private String publicKeyDer;

    @JsonProperty("cyoda_key_expires_at")
    private String cyodaKeyExpiresAt;

    @JsonProperty("last_rotated_at")
    private String lastRotatedAt;

    @Override
    public OperationSpecification getModelKey() {
        return new OperationSpecification.Entity(MODEL_SPEC, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return keyId != null && !keyId.isBlank()
                && encryptedPrivateKey != null && !encryptedPrivateKey.isBlank()
                && publicKeyDer != null && !publicKeyDer.isBlank();
    }

    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }

    public String getEncryptedPrivateKey() { return encryptedPrivateKey; }
    public void setEncryptedPrivateKey(String encryptedPrivateKey) { this.encryptedPrivateKey = encryptedPrivateKey; }

    public String getPublicKeyDer() { return publicKeyDer; }
    public void setPublicKeyDer(String publicKeyDer) { this.publicKeyDer = publicKeyDer; }

    public String getCyodaKeyExpiresAt() { return cyodaKeyExpiresAt; }
    public void setCyodaKeyExpiresAt(String cyodaKeyExpiresAt) { this.cyodaKeyExpiresAt = cyodaKeyExpiresAt; }

    public String getLastRotatedAt() { return lastRotatedAt; }
    public void setLastRotatedAt(String lastRotatedAt) { this.lastRotatedAt = lastRotatedAt; }
}
