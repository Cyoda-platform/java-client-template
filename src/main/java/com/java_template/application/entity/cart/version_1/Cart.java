package com.java_template.application.entity.cart.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class Cart implements CyodaEntity {
    public static final String ENTITY_NAME = "Cart";
    public static final Integer ENTITY_VERSION = 1;

    private String cartId;
    private String customerId;
    private List<CartItem> items;
    private String status;
    private LocalDateTime createdAt;

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
        if (cartId == null || cartId.isBlank()) return false;
        if (customerId == null || customerId.isBlank()) return false;
        if (items == null || items.isEmpty()) return false;
        if (status == null || status.isBlank()) return false;
        if (createdAt == null) return false;
        for (CartItem item : items) {
            if (!item.isValid()) return false;
        }
        return true;
    }
}

@Data
class CartItem {
    private String productId;
    private Integer quantity;

    public boolean isValid() {
        return productId != null && !productId.isBlank() && quantity != null && quantity > 0;
    }
}
