package com.java_template.application.entity.cart.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Objects;

@Data
public class Cart implements CyodaEntity {
    public static final String ENTITY_NAME = "Cart";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String cartId; // serialized UUID / technical id
    private String userId; // serialized UUID reference to User
    private String reservationBatchId; // serialized UUID reference for reservation batch
    private String status; // use String for enum-like values (e.g., "ACTIVE")
    private String createdAt; // ISO timestamp as String
    private String updatedAt; // ISO timestamp as String
    private Double grandTotal;
    private Integer totalItems;
    private List<Line> lines;

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
        // Validate required string identifiers and timestamps
        if (cartId == null || cartId.isBlank()) return false;
        if (userId == null || userId.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;

        // Validate numeric totals
        if (grandTotal == null || grandTotal < 0d) return false;
        if (totalItems == null || totalItems < 0) return false;

        // Validate lines
        if (lines == null || lines.isEmpty()) return false;
        int sumQty = 0;
        for (Line line : lines) {
            if (line == null) return false;
            if (!line.isValid()) return false;
            sumQty += line.getQty();
        }

        // totalItems should equal sum of quantities
        if (totalItems.intValue() != sumQty) return false;

        return true;
    }

    @Data
    public static class Line {
        private String name;
        private Double price;
        private Integer qty;
        private String sku;

        public boolean isValid() {
            if (name == null || name.isBlank()) return false;
            if (sku == null || sku.isBlank()) return false;
            if (price == null || price < 0d) return false;
            if (qty == null || qty <= 0) return false;
            return true;
        }
    }
}