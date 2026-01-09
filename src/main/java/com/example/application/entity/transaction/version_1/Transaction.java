package com.example.application.entity.transaction.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Transaction Entity - Represents financial transactions for compliance monitoring
 * 
 * This entity captures transaction details including:
 * - Transaction identification and account references
 * - Amount and currency information
 * - Transaction type and status
 * - Geographic and channel information
 * - Compliance risk scoring and watchlist matching
 * - Raw and normalized payload data
 */
@Data
public class Transaction implements CyodaEntity {
    public static final String ENTITY_NAME = "Transaction";
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier
    private String id;
    
    // Required account reference
    private String accountId;
    
    // Optional account references
    private String fromAccountId;
    private String toAccountId;
    
    // Transaction amount and currency
    private BigDecimal amount;
    private String currency;
    
    // Transaction timing
    private LocalDateTime timestamp;
    
    // Transaction type enumeration
    private TransactionType type;
    
    // Transaction status enumeration
    private TransactionStatus status;
    
    // Channel information
    private String channel;
    
    // Optional merchant information
    private String merchant;
    
    // Geographic information
    private String country;
    private GeoLocation geoLocation;
    
    // Payload data
    private String rawPayload;
    private String normalizedPayload;
    
    // Compliance and risk data
    private List<String> flags;
    private Double score;
    private List<String> matchedWatchlistIds;
    
    // Additional metadata
    private Map<String, Object> metadata;

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
        return id != null && !id.isBlank() && 
               accountId != null && !accountId.isBlank();
    }

    /**
     * Nested class for geographic location information
     */
    @Data
    public static class GeoLocation {
        private Double latitude;
        private Double longitude;
        private String city;
        private String country;
    }

    /**
     * Transaction type enumeration
     */
    public enum TransactionType {
        PAYMENT,
        TRANSFER,
        DEPOSIT,
        WITHDRAWAL
    }

    /**
     * Transaction status enumeration
     */
    public enum TransactionStatus {
        PENDING,
        COMPLETED,
        FAILED,
        REVERSED
    }
}

