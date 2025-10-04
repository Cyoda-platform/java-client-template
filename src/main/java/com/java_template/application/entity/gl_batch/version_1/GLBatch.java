package com.java_template.application.entity.gl_batch.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ABOUTME: This entity represents a batch of summarized accounting entries prepared
 * at the end of a month for posting to the General Ledger system.
 */
@Data
public class GLBatch implements CyodaEntity {
    public static final String ENTITY_NAME = GLBatch.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String batchId;
    
    // Required core business fields
    private String period; // e.g., "2025-09" for September 2025
    private String exportFormat; // e.g., "CSV", "JSON", "XML"
    private String status; // Managed by workflow state machine
    
    // Control totals for validation
    private GLControlTotals controlTotals;
    
    // GL lines within this batch
    private List<GLLine> glLines;
    
    // Export information
    private GLExport export;
    
    // Audit information
    private GLBatchAudit audit;

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
        return batchId != null && !batchId.trim().isEmpty() &&
               period != null && !period.trim().isEmpty() &&
               exportFormat != null && !exportFormat.trim().isEmpty();
    }

    /**
     * Nested class for control totals
     */
    @Data
    public static class GLControlTotals {
        private BigDecimal totalDebits;
        private BigDecimal totalCredits;
        private Integer lineItemCount;
        private Boolean isBalanced; // totalDebits == totalCredits
    }

    /**
     * Nested class for individual GL lines
     */
    @Data
    public static class GLLine {
        private String glLineId;
        private String glAccount; // e.g., "1100-Interest-Receivable"
        private String description; // e.g., "Total interest accrued for period 2025-09"
        private String type; // "DEBIT" or "CREDIT"
        private BigDecimal amount;
        private String costCenter; // Optional
        private String product; // Optional
        private String reference; // Optional reference to source transactions
    }

    /**
     * Nested class for export information
     */
    @Data
    public static class GLExport {
        private String fileName;
        private String filePath;
        private String fileFormat;
        private Long fileSizeBytes;
        private String checksum; // For file integrity verification
        private LocalDateTime exportedAt;
        private String exportedBy;
        private String destinationSystem; // e.g., "SAP", "Oracle GL"
        private String transmissionMethod; // e.g., "SFTP", "API", "EMAIL"
        private String transmissionStatus; // e.g., "SENT", "ACKNOWLEDGED", "FAILED"
    }

    /**
     * Nested class for audit information
     */
    @Data
    public static class GLBatchAudit {
        private LocalDateTime preparedAt;
        private String preparedBy;
        private List<GLApproval> approvals;
        private LocalDateTime exportedAt;
        private String exportedBy;
        private LocalDateTime postedAt; // When GL system confirms posting
        private LocalDateTime archivedAt;
    }

    /**
     * Nested class for approval tracking
     */
    @Data
    public static class GLApproval {
        private String approverUserId;
        private String approverName;
        private String approverRole; // e.g., "Finance Manager"
        private LocalDateTime approvedAt;
        private String approvalType; // e.g., "MAKER", "CHECKER"
        private String comments;
    }
}
