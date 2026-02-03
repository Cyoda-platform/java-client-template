package com.cyoda.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public class Trade {
    private String id;
    private String orderId;
    private String instrumentId;
    private String executionId;
    private double price;
    private long quantity;
    private String venue;
    private Instant tradeTimestamp;
    private LocalDate settlementDate;
    private String counterpartyId;
    private double fees;
    private String currency;

    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;

    public Trade() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getInstrumentId() { return instrumentId; }
    public void setInstrumentId(String instrumentId) { this.instrumentId = instrumentId; }

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public long getQuantity() { return quantity; }
    public void setQuantity(long quantity) { this.quantity = quantity; }

    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }

    public Instant getTradeTimestamp() { return tradeTimestamp; }
    public void setTradeTimestamp(Instant tradeTimestamp) { this.tradeTimestamp = tradeTimestamp; }

    public LocalDate getSettlementDate() { return settlementDate; }
    public void setSettlementDate(LocalDate settlementDate) { this.settlementDate = settlementDate; }

    public String getCounterpartyId() { return counterpartyId; }
    public void setCounterpartyId(String counterpartyId) { this.counterpartyId = counterpartyId; }

    public double getFees() { return fees; }
    public void setFees(double fees) { this.fees = fees; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    // Basic validation
    public void validate() {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(instrumentId, "instrumentId is required");
        Objects.requireNonNull(executionId, "executionId is required");
        if (price < 0) throw new IllegalArgumentException("price must be >= 0");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be > 0");
        Objects.requireNonNull(tradeTimestamp, "tradeTimestamp is required");
        Objects.requireNonNull(currency, "currency is required");
    }
}
