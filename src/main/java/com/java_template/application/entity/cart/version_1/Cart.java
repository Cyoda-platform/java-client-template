package com.java_template.application.entity.cart.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class Cart implements CyodaEntity {
    public static final String ENTITY_NAME = "Cart"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id;
    private List<CartItem> items;
    private String lastUpdated; // ISO-8601 timestamp as String
    private String status; // use String for enums
    private String userId; // foreign key as serialized UUID

    public Cart() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (id == null || id.isBlank()) return false;
        if (userId == null || userId.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (lastUpdated == null || lastUpdated.isBlank()) return false;
        if (items == null || items.isEmpty()) return false;
        for (CartItem item : items) {
            if (item == null) return false;
            if (item.getProductId() == null || item.getProductId().isBlank()) return false;
            if (item.getQty() == null || item.getQty() <= 0) return false;
            if (item.getPriceSnapshot() == null || item.getPriceSnapshot() < 0.0) return false;
        }
        return true;
    }

    @Data
    public static class CartItem {
        private Double priceSnapshot;
        private String productId;
        private Integer qty;
    }
}