package com.java_template.application.entity.cart.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Cart implements CyodaEntity {
    public static final String ENTITY_NAME = "Cart";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id;
    private String userId; // foreign key reference as serialized UUID string
    private String createdAt; // ISO-8601 timestamp as String
    private List<Item> items = new ArrayList<>();
    private String status;
    private Double total;

    public Cart() {}

    @Data
    public static class Item {
        private Double priceAtAdd;
        private String productId; // foreign key reference as serialized UUID string
        private Integer quantity;
    }

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
        if (createdAt == null || createdAt.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (items == null) return false;
        double sum = 0.0;
        for (Item it : items) {
            if (it == null) return false;
            if (it.getProductId() == null || it.getProductId().isBlank()) return false;
            if (it.getQuantity() == null || it.getQuantity() <= 0) return false;
            if (it.getPriceAtAdd() == null || it.getPriceAtAdd() < 0) return false;
            sum += it.getPriceAtAdd() * it.getQuantity();
        }
        if (total == null || total < 0) return false;
        // Allow small rounding differences
        if (Math.abs(total - sum) > 0.01) return false;
        return true;
    }
}