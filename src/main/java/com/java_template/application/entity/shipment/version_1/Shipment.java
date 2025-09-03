package com.java_template.application.entity.shipment.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.Instant;
import java.util.List;

@Data
public class Shipment implements CyodaEntity {
    public static final String ENTITY_NAME = Shipment.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required fields
    private String shipmentId;
    private String orderId;
    private List<ShipmentLine> lines;
    
    // Auto-generated timestamps
    private Instant createdAt;
    private Instant updatedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return shipmentId != null && !shipmentId.trim().isEmpty() &&
               orderId != null && !orderId.trim().isEmpty() &&
               lines != null && !lines.isEmpty() &&
               lines.stream().allMatch(ShipmentLine::isValid);
    }

    @Data
    public static class ShipmentLine {
        private String sku;
        private Integer qtyOrdered;
        private Integer qtyPicked;
        private Integer qtyShipped;
        
        public boolean isValid() {
            return sku != null && !sku.trim().isEmpty() &&
                   qtyOrdered != null && qtyOrdered > 0 &&
                   qtyPicked != null && qtyPicked >= 0 && qtyPicked <= qtyOrdered &&
                   qtyShipped != null && qtyShipped >= 0 && qtyShipped <= qtyPicked;
        }
        
        public boolean isFullyPicked() {
            return qtyPicked != null && qtyOrdered != null && 
                   qtyPicked.equals(qtyOrdered);
        }
        
        public boolean isFullyShipped() {
            return qtyShipped != null && qtyPicked != null && 
                   qtyShipped.equals(qtyPicked);
        }
    }
    
    // Helper methods for business logic
    public boolean hasValidLines() {
        return lines != null && !lines.isEmpty() && 
               lines.stream().allMatch(ShipmentLine::isValid);
    }
    
    public boolean isFullyPicked() {
        return lines != null && !lines.isEmpty() &&
               lines.stream().allMatch(ShipmentLine::isFullyPicked);
    }
    
    public boolean isFullyShipped() {
        return lines != null && !lines.isEmpty() &&
               lines.stream().allMatch(ShipmentLine::isFullyShipped);
    }
    
    public void markAllPicked() {
        if (lines != null) {
            for (ShipmentLine line : lines) {
                if (line.getQtyOrdered() != null) {
                    line.setQtyPicked(line.getQtyOrdered());
                }
            }
            updatedAt = Instant.now();
        }
    }
    
    public void markAllShipped() {
        if (lines != null) {
            for (ShipmentLine line : lines) {
                if (line.getQtyPicked() != null) {
                    line.setQtyShipped(line.getQtyPicked());
                }
            }
            updatedAt = Instant.now();
        }
    }
}
