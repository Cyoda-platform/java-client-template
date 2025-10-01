package com.java_template.application.entity.medication.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Medication entity for IP (Investigational Product) accountability
 * Tracks lots, dispenses, and returns for medication management
 */
@Data
public class Medication implements CyodaEntity {
    public static final String ENTITY_NAME = Medication.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String medicationId;
    
    // Study association
    private String studyId;
    
    // Lot information
    private String productName;
    private String lotNumber;
    private LocalDate expiryDate;
    private Integer quantityOnHand;
    private String storageConditions;
    
    // Transaction history
    private List<DispenseRecord> dispenses;
    private List<ReturnRecord> returns;
    
    // Audit fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return medicationId != null && !medicationId.trim().isEmpty() &&
               studyId != null && !studyId.trim().isEmpty() &&
               productName != null && !productName.trim().isEmpty() &&
               lotNumber != null && !lotNumber.trim().isEmpty();
    }

    /**
     * Nested class for dispense records
     */
    @Data
    public static class DispenseRecord {
        private String dispenseId;
        private String subjectId;
        private LocalDate date;
        private Integer quantity;
        private String performedBy;
    }

    /**
     * Nested class for return records
     */
    @Data
    public static class ReturnRecord {
        private String returnId;
        private String subjectId;
        private LocalDate date;
        private Integer quantity;
        private String reason;
    }
}
