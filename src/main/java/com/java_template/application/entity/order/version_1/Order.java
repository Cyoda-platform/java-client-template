package com.java_template.application.entity.order.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Objects;

@Data
public class Order implements CyodaEntity {
    public static final String ENTITY_NAME = "Order";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id;
    private String cartId; // foreign key (serialized UUID)
    private String userId; // foreign key (serialized UUID)
    private String billingAddressId; // foreign key (serialized UUID)
    private String shippingAddressId; // foreign key (serialized UUID)
    private List<Item> itemsSnapshot;
    private Double totalAmount;
    private String status; // enum represented as String
    private String createdAt;
    private String updatedAt;

    public Order() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate String fields using isBlank()
        if (id == null || id.isBlank()) return false;
        if (cartId == null || cartId.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        // userId may be optional in some flows, but if present should be non-blank
        if (userId != null && userId.isBlank()) return false;
        // Address fields if present should be non-blank
        if (billingAddressId != null && billingAddressId.isBlank()) return false;
        if (shippingAddressId != null && shippingAddressId.isBlank()) return false;
        // totalAmount must be present and non-negative
        if (totalAmount == null || totalAmount < 0) return false;
        // itemsSnapshot must be present and each item must be valid
        if (itemsSnapshot == null || itemsSnapshot.isEmpty()) return false;
        for (Item it : itemsSnapshot) {
            if (it == null || !it.isValid()) return false;
        }
        return true;
    }

    @Data
    public static class Item {
        private String productId; // foreign key (serialized UUID / SKU)
        private Integer quantity;
        private Double unitPrice;

        public boolean isValid() {
            if (productId == null || productId.isBlank()) return false;
            if (quantity == null || quantity <= 0) return false;
            if (unitPrice == null || unitPrice < 0) return false;
            return true;
        }
    }
}