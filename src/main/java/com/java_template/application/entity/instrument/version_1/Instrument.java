package com.java_template.application.entity.instrument.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

/**
 * Instrument Entity - Represents financial instruments (equities and derivatives)
 * Supports both equity and derivative instruments with Greeks for options
 */
@Data
public class Instrument implements CyodaEntity {
    public static final String ENTITY_NAME = Instrument.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier - unique symbol
    private String symbol;

    // Instrument type: EQUITY, OPTION, FUTURE, SWAP, etc.
    private String instrumentType;

    // Exchange where instrument is traded
    private String exchange;

    // Instrument description
    private String description;

    // Currency for pricing
    private String currency;

    // Current market price
    private Double currentPrice;

    // Bid price
    private Double bidPrice;

    // Ask price
    private Double askPrice;

    // Last trade price
    private Double lastTradePrice;

    // Daily volume
    private Long volume;

    // For derivatives: underlying instrument symbol
    private String underlyingSymbol;

    // For options: strike price
    private Double strikePrice;

    // For options: expiration date
    private LocalDateTime expirationDate;

    // For options: option type (CALL or PUT)
    private String optionType;

    // Greeks for derivatives
    private Greeks greeks;

    // Lot size / contract multiplier
    private Integer lotSize;

    // Minimum price increment (tick size)
    private Double tickSize;

    // Status: ACTIVE, INACTIVE, SUSPENDED
    private String status;

    // Timestamp when instrument was created
    private LocalDateTime createdAt;

    // Timestamp when instrument was last updated
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
        return symbol != null && !symbol.trim().isEmpty() &&
               instrumentType != null && !instrumentType.trim().isEmpty() &&
               exchange != null && !exchange.trim().isEmpty();
    }

    /**
     * Greeks for derivatives (delta, gamma, vega, theta, rho)
     */
    @Data
    public static class Greeks {
        // Delta: rate of change of option price with respect to underlying price
        private Double delta;

        // Gamma: rate of change of delta with respect to underlying price
        private Double gamma;

        // Vega: sensitivity to volatility changes
        private Double vega;

        // Theta: time decay (sensitivity to time passage)
        private Double theta;

        // Rho: sensitivity to interest rate changes
        private Double rho;

        // Implied volatility
        private Double impliedVolatility;
    }
}

