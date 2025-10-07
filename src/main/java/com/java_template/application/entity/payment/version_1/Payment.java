package com.java_template.application.entity.payment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ABOUTME: This entity represents a single payment received from a borrower
 * and details how the funds were allocated to interest, fees, and principal.
 */
@Data
public class Payment implements CyodaEntity {
    public static final String ENTITY_NAME = Payment.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String paymentId;

    // Required core business fields
    private String loanId; // Reference to the Loan entity
    private String payerPartyId; // Reference to the payer Party
    private BigDecimal paymentAmount;
    private String currency;
    private LocalDate valueDate; // Date when payment is effective for accounting
    private LocalDate receivedDate; // Date when payment was actually received

    // Payment processing fields
    private String paymentMethod; // e.g., "BANK_TRANSFER", "CHEQUE", "WIRE"
    private String reference; // External reference (e.g., bank reference)

    // Allocation details (populated after processing)
    private PaymentAllocation allocation;

    // Sub-ledger entries (populated after posting)
    private List<PaymentSubLedgerEntry> subLedgerEntries;

    // Audit information
    private PaymentAudit audit;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        // Validate required fields
        return paymentId != null && !paymentId.trim().isEmpty() &&
               loanId != null && !loanId.trim().isEmpty() &&
               payerPartyId != null && !payerPartyId.trim().isEmpty() &&
               paymentAmount != null && paymentAmount.compareTo(BigDecimal.ZERO) > 0 &&
               currency != null && !currency.trim().isEmpty() &&
               valueDate != null &&
               receivedDate != null;
    }

    /**
     * Nested class for payment allocation details
     */
    @Data
    public static class PaymentAllocation {
        private BigDecimal interestAllocated;
        private BigDecimal feesAllocated;
        private BigDecimal principalAllocated;
        private BigDecimal excessFunds; // For overpayments
    }

    /**
     * Nested class for sub-ledger entries
     */
    @Data
    public static class PaymentSubLedgerEntry {
        private String entryId;
        private String account; // e.g., "Cash", "Interest Receivable", "Loan Principal"
        private String type; // "DEBIT" or "CREDIT"
        private BigDecimal amount;
    }

    /**
     * Nested class for audit information
     */
    @Data
    public static class PaymentAudit {
        private LocalDateTime createdAt;
        private String createdBy; // User who created the payment
        private LocalDateTime postedAt;
        private String postedBy; // User who posted the payment (may be system)
        private LocalDateTime modifiedAt;
        private String modifiedBy;
    }
}
