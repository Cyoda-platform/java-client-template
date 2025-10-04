package com.java_template.application.entity.gl_line.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ABOUTME: This entity represents a single debit or credit line within
 * a GL batch, containing the detailed journal entry information.
 */
@Data
public class GLLine implements CyodaEntity {
    public static final String ENTITY_NAME = GLLine.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String glLineId;
    
    // Required core business fields
    private String batchId; // Reference to parent GLBatch
    private String glAccount; // e.g., "1100-Interest-Receivable"
    private String description; // e.g., "Total interest accrued for period 2025-09"
    private String type; // "DEBIT" or "CREDIT"
    private BigDecimal amount;
    
    // Optional classification fields
    private String costCenter;
    private String product;
    private String department;
    private String reference; // Reference to source transactions
    
    // Source tracking
    private GLLineSource source;
    
    // Audit information
    private GLLineAudit audit;

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
        return glLineId != null && !glLineId.trim().isEmpty() &&
               batchId != null && !batchId.trim().isEmpty() &&
               glAccount != null && !glAccount.trim().isEmpty() &&
               description != null && !description.trim().isEmpty() &&
               type != null && ("DEBIT".equals(type) || "CREDIT".equals(type)) &&
               amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Nested class for source tracking
     */
    @Data
    public static class GLLineSource {
        private String sourceType; // e.g., "ACCRUAL", "PAYMENT", "ADJUSTMENT"
        private String sourceEntityType; // e.g., "Accrual", "Payment"
        private String sourceEntityId; // ID of the source entity
        private String aggregationMethod; // e.g., "SUM", "COUNT", "AVERAGE"
        private Integer sourceRecordCount; // Number of source records aggregated
        private String period; // Period covered by this line
    }

    /**
     * Nested class for audit information
     */
    @Data
    public static class GLLineAudit {
        private LocalDateTime createdAt;
        private String createdBy;
        private LocalDateTime modifiedAt;
        private String modifiedBy;
        private String creationReason; // e.g., "MONTH_END_ACCRUAL", "PAYMENT_ALLOCATION"
    }
}
