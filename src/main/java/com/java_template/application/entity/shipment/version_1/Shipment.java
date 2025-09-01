package com.java_template.application.entity.shipment.version_1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Shipment entity representing a single shipment per order for demo purposes.
 */
public class Shipment implements CyodaEntity {

    @JsonProperty("shipmentId")
    @NotBlank(message = "Shipment ID is required")
    private String shipmentId;

    @JsonProperty("orderId")
    @NotBlank(message = "Order ID is required")
    private String orderId;

    @JsonProperty("lines")
    @NotNull(message = "Lines are required")
    @Valid
    private List<ShipmentLine> lines;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;

    // Default constructor
    public Shipment() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Constructor with required fields
    public Shipment(String shipmentId, String orderId, List<ShipmentLine> lines) {
        this.shipmentId = shipmentId;
        this.orderId = orderId;
        this.lines = lines;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @Override
    @JsonIgnore
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("Shipment");
        modelSpec.setVersion(1);
        return new OperationSpecification.Entity(modelSpec, "Shipment");
    }

    @Override
    @JsonIgnore
    public boolean isValid() {
        return shipmentId != null && !shipmentId.trim().isEmpty() &&
               orderId != null && !orderId.trim().isEmpty() &&
               lines != null && !lines.isEmpty();
    }

    // Getters and Setters
    public String getShipmentId() {
        return shipmentId;
    }

    public void setShipmentId(String shipmentId) {
        this.shipmentId = shipmentId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public List<ShipmentLine> getLines() {
        return lines;
    }

    public void setLines(List<ShipmentLine> lines) {
        this.lines = lines;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Shipment shipment = (Shipment) o;
        return Objects.equals(shipmentId, shipment.shipmentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shipmentId);
    }

    @Override
    public String toString() {
        return "Shipment{" +
                "shipmentId='" + shipmentId + '\'' +
                ", orderId='" + orderId + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }

    /**
     * Shipment line item representing a product in the shipment.
     */
    public static class ShipmentLine {
        @JsonProperty("sku")
        @NotBlank(message = "SKU is required")
        private String sku;

        @JsonProperty("qtyOrdered")
        @NotNull(message = "Quantity ordered is required")
        @Min(value = 0, message = "Quantity ordered must be non-negative")
        private Integer qtyOrdered;

        @JsonProperty("qtyPicked")
        @NotNull(message = "Quantity picked is required")
        @Min(value = 0, message = "Quantity picked must be non-negative")
        private Integer qtyPicked;

        @JsonProperty("qtyShipped")
        @NotNull(message = "Quantity shipped is required")
        @Min(value = 0, message = "Quantity shipped must be non-negative")
        private Integer qtyShipped;

        // Default constructor
        public ShipmentLine() {
            this.qtyPicked = 0;
            this.qtyShipped = 0;
        }

        // Constructor with all fields
        public ShipmentLine(String sku, Integer qtyOrdered, Integer qtyPicked, Integer qtyShipped) {
            this.sku = sku;
            this.qtyOrdered = qtyOrdered;
            this.qtyPicked = qtyPicked != null ? qtyPicked : 0;
            this.qtyShipped = qtyShipped != null ? qtyShipped : 0;
        }

        // Constructor with required fields (picked and shipped default to 0)
        public ShipmentLine(String sku, Integer qtyOrdered) {
            this.sku = sku;
            this.qtyOrdered = qtyOrdered;
            this.qtyPicked = 0;
            this.qtyShipped = 0;
        }

        // Getters and Setters
        public String getSku() {
            return sku;
        }

        public void setSku(String sku) {
            this.sku = sku;
        }

        public Integer getQtyOrdered() {
            return qtyOrdered;
        }

        public void setQtyOrdered(Integer qtyOrdered) {
            this.qtyOrdered = qtyOrdered;
        }

        public Integer getQtyPicked() {
            return qtyPicked;
        }

        public void setQtyPicked(Integer qtyPicked) {
            this.qtyPicked = qtyPicked;
        }

        public Integer getQtyShipped() {
            return qtyShipped;
        }

        public void setQtyShipped(Integer qtyShipped) {
            this.qtyShipped = qtyShipped;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ShipmentLine that = (ShipmentLine) o;
            return Objects.equals(sku, that.sku);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sku);
        }

        @Override
        public String toString() {
            return "ShipmentLine{" +
                    "sku='" + sku + '\'' +
                    ", qtyOrdered=" + qtyOrdered +
                    ", qtyPicked=" + qtyPicked +
                    ", qtyShipped=" + qtyShipped +
                    '}';
        }
    }
}
