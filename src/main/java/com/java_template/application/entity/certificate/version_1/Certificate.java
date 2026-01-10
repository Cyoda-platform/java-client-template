package com.java_template.application.entity.certificate.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * ABOUTME: Certificate entity represents a digital certificate and manages its complete lifecycle
 * from creation through expiration, including issuance, renewal, and revocation states.
 */
@Data
public class Certificate implements CyodaEntity {
    public static final String ENTITY_NAME = Certificate.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String certificateId;
    
    // Core certificate fields
    private String commonName;
    private String organizationName;
    private String organizationUnit;
    private String country;
    private String state;
    private String locality;
    
    // Certificate technical details
    private String serialNumber;
    private String issuer;
    private String publicKeyAlgorithm;
    private Integer keySize;
    
    // Certificate lifecycle dates
    private LocalDateTime issuedDate;
    private LocalDateTime expirationDate;
    private LocalDateTime revokedDate;
    
    // Certificate status and metadata
    private String certificateType;
    private String signatureAlgorithm;
    private String thumbprint;
    private String description;
    
    // Certificate content
    private String certificateContent;
    private String privateKeyContent;
    
    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(org.cyoda.cloud.api.event.common.EntityMetadata metadata) {
        // Validate required fields
        return certificateId != null && !certificateId.trim().isEmpty() &&
               commonName != null && !commonName.trim().isEmpty() &&
               serialNumber != null && !serialNumber.trim().isEmpty();
    }
}

