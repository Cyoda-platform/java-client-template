package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.List;

@Data
public class Cart implements CyodaEntity {
    public static final String ENTITY_NAME = "Cart";

    private String cartId;
    private String customerId;
    private List<CartLine> items;
    private Integer totalItems;
    private Double grandTotal;
    private String status; // e.g., NEW, ACTIVE, CHECKING_OUT, CONVERTED
    private String createdAt;
    private String updatedAt;

    @Data
    public static class CartLine {
        private String sku;
        private Integer quantity;
        private Double price;
    }

    public Cart() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (cartId == null || cartId.isBlank()) return false;
        if (customerId == null || customerId.isBlank()) return false;
        if (items == null || items.isEmpty()) return false;
        if (status == null || status.isBlank()) return false;
        if (totalItems == null || totalItems < 0) return false;
        if (grandTotal == null || grandTotal < 0) return false;
        for (CartLine line : items) {
            if (line.getSku() == null || line.getSku().isBlank()) return false;
            if (line.getQuantity() == null || line.getQuantity() <= 0) return false;
            if (line.getPrice() == null || line.getPrice() < 0) return false;
        }
        return true;
    }
}
