package com.java_template.application.entity.gl_batch.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ABOUTME: This file contains the GLBatch entity representing monthly summarized journal
 * entries for export to the General Ledger system with maker/checker controls.
 */
@Data
public class GLBatch implements CyodaEntity {
    public static final String ENTITY_NAME = GLBatch.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String glBatchId;
    
    // Period information
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String periodCode; // YYYY-MM format
    
    // Batch summary
    private Integer totalEntries;
    private BigDecimal totalDebits;
    private BigDecimal totalCredits;
    private BigDecimal balanceDifference; // Should be zero for balanced batch
    
    // Journal entries
    private List<GLEntry> entries;
    
    // Export information
    private String exportFileName;
    private String exportFormat; // CSV, JSON, XML
    private String exportPath;
    private LocalDateTime exportedAt;
    
    // GL system integration
    private String glSystemBatchId;
    private LocalDateTime postedToGLAt;
    private String glAcknowledgmentId;
    
    // Maker/Checker workflow
    private GLApproval approval;
    
    // Status and metadata
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return glBatchId != null && !glBatchId.trim().isEmpty() &&
               periodStart != null &&
               periodEnd != null &&
               periodCode != null && !periodCode.trim().isEmpty();
    }

    /**
     * Nested class for GL journal entries
     */
    @Data
    public static class GLEntry {
        private String entryId;
        private String accountCode;
        private String accountName;
        private String description;
        private BigDecimal debitAmount;
        private BigDecimal creditAmount;
        private String dimension1; // Company
        private String dimension2; // Product
        private String dimension3; // Branch
        private String reference;
    }

    /**
     * Nested class for maker/checker approval
     */
    @Data
    public static class GLApproval {
        private String preparedBy;
        private LocalDateTime preparedAt;
        private String approvedBy;
        private LocalDateTime approvedAt;
        private String approverRole;
        private String approvalComments;
    }
}
