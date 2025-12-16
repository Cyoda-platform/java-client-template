package com.java_template.application.entity.settlement_instruction.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * SettlementInstruction Entity - Represents settlement instructions for trades
 * Tracks clearing and settlement process with status and reconciliation
 */
@Data
public class SettlementInstruction implements CyodaEntity {
    public static final String ENTITY_NAME = SettlementInstruction.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier - unique settlement instruction ID
    private String settlementInstructionId;

    // Reference to the trade being settled
    private String tradeId;

    // Portfolio/Account ID
    private String portfolioId;

    // Instrument symbol
    private String instrumentSymbol;

    // Settlement side: BUY or SELL
    private String side;

    // Settlement quantity
    private Long quantity;

    // Settlement price
    private Double settlementPrice;

    // Settlement amount
    private Double settlementAmount;

    // Settlement date
    private LocalDateTime settlementDate;

    // Delivery date
    private LocalDateTime deliveryDate;

    // Settlement status: PENDING, MATCHED, CONFIRMED, SETTLED, FAILED
    private String settlementStatus;

    // Counterparty
    private String counterparty;

    // Clearing house
    private String clearingHouse;

    // Depository/Custodian
    private String depository;

    // Delivery instruction details
    private String deliveryInstructions;

    // Payment instruction details
    private String paymentInstructions;

    // Reconciliation status: PENDING, MATCHED, UNMATCHED
    private String reconciliationStatus;

    // Failure reason (if settlement failed)
    private String failureReason;

    // Settlement instruction creation timestamp
    private LocalDateTime createdAt;

    // Settlement instruction last updated timestamp
    private LocalDateTime updatedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(EntityMetadata metadata) {
        return settlementInstructionId != null && !settlementInstructionId.trim().isEmpty() &&
               tradeId != null && !tradeId.trim().isEmpty() &&
               portfolioId != null && !portfolioId.trim().isEmpty() &&
               instrumentSymbol != null && !instrumentSymbol.trim().isEmpty() &&
               quantity != null && quantity > 0 &&
               settlementPrice != null && settlementPrice > 0;
    }
}

