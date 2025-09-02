package com.java_template.application.entity.shipment.version_1;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Shipment entity representing a single shipment per order for tracking fulfillment.
 */
public class Shipment implements CyodaEntity {

    @JsonProperty("shipmentId")
    private String shipmentId;

    @JsonProperty("orderId")
    private String orderId;

    @JsonProperty("lines")
    private List<ShipmentLine> lines;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;

    // Default constructor
    public Shipment() {}

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

    // Getters and setters
    public String getShipmentId() { return shipmentId; }
    public void setShipmentId(String shipmentId) { this.shipmentId = shipmentId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public List<ShipmentLine> getLines() { return lines; }
    public void setLines(List<ShipmentLine> lines) { this.lines = lines; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    // Helper method to update timestamp
    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }

    // Inner class for shipment lines
    public static class ShipmentLine {
        @JsonProperty("sku")
        private String sku;

        @JsonProperty("qtyOrdered")
        private Integer qtyOrdered;

        @JsonProperty("qtyPicked")
        private Integer qtyPicked;

        @JsonProperty("qtyShipped")
        private Integer qtyShipped;

        public ShipmentLine() {}

        public ShipmentLine(String sku, Integer qtyOrdered) {
            this.sku = sku;
            this.qtyOrdered = qtyOrdered;
            this.qtyPicked = 0;
            this.qtyShipped = 0;
        }

        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }

        public Integer getQtyOrdered() { return qtyOrdered; }
        public void setQtyOrdered(Integer qtyOrdered) { this.qtyOrdered = qtyOrdered; }

        public Integer getQtyPicked() { return qtyPicked; }
        public void setQtyPicked(Integer qtyPicked) { this.qtyPicked = qtyPicked; }

        public Integer getQtyShipped() { return qtyShipped; }
        public void setQtyShipped(Integer qtyShipped) { this.qtyShipped = qtyShipped; }

        public boolean isValid() {
            return sku != null && !sku.trim().isEmpty() &&
                   qtyOrdered != null && qtyOrdered > 0 &&
                   qtyPicked != null && qtyPicked >= 0 &&
                   qtyShipped != null && qtyShipped >= 0;
        }

        public boolean isFullyPicked() {
            return qtyPicked != null && qtyOrdered != null && qtyPicked.equals(qtyOrdered);
        }

        public boolean isFullyShipped() {
            return qtyShipped != null && qtyPicked != null && qtyShipped.equals(qtyPicked);
        }
    }

    // Helper methods for shipment operations
    public boolean areAllItemsPicked() {
        if (lines == null || lines.isEmpty()) {
            return false;
        }
        return lines.stream().allMatch(ShipmentLine::isFullyPicked);
    }

    public boolean areAllItemsShipped() {
        if (lines == null || lines.isEmpty()) {
            return false;
        }
        return lines.stream().allMatch(ShipmentLine::isFullyShipped);
    }

    public void markAllItemsPicked() {
        if (lines != null) {
            lines.forEach(line -> line.setQtyPicked(line.getQtyOrdered()));
            updateTimestamp();
        }
    }

    public void markAllItemsShipped() {
        if (lines != null) {
            lines.forEach(line -> line.setQtyShipped(line.getQtyPicked()));
            updateTimestamp();
        }
    }

    public ShipmentLine findLineBySkuOrNull(String sku) {
        if (lines == null || sku == null) {
            return null;
        }
        return lines.stream()
                .filter(line -> sku.equals(line.getSku()))
                .findFirst()
                .orElse(null);
    }
}
