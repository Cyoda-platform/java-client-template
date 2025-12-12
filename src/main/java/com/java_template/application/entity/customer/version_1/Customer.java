package com.java_template.application.entity.customer.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Customer Entity for Customer Management System
 * 
 * This entity represents a customer in the system with lifecycle management
 * through states: initial_state, onboarding, verification_pending, verified, 
 * active, suspended, termination_pending, terminated.
 * 
 * Business fields include customer identification, contact information,
 * and metadata for extensibility.
 */
@Data
public class Customer implements CyodaEntity {
    public static final String ENTITY_NAME = Customer.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String customerId;
    
    // Required core business fields
    private String name;
    private String email;
    
    // Optional fields
    private String phone;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Verification related fields
    private VerificationInfo verification;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required fields
        return customerId != null && !customerId.trim().isEmpty() &&
               name != null && !name.trim().isEmpty() &&
               email != null && !email.trim().isEmpty() && isValidEmail(email);
    }
    
    /**
     * Basic email validation
     */
    private boolean isValidEmail(String email) {
        return email.contains("@") && email.contains(".");
    }

    /**
     * Nested class for verification information
     * Used by verification processors to track verification status
     */
    @Data
    public static class VerificationInfo {
        private String status; // SUCCESS, FAILED, PENDING, NOT_STARTED
        private String provider;
        private String requestId;
        private LocalDateTime requestedAt;
        private LocalDateTime completedAt;
        private String failureReason;
        private Map<String, Object> providerResponse;
    }
}
