package com.java_template.application.entity.cart.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.ArrayList;

@Data
public class Cart implements CyodaEntity {
    public static final String ENTITY_NAME = "Cart";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String cartId;
    private String userId; // serialized UUID reference, nullable
    private List<CartLine> lines = new ArrayList<>();
    private Integer totalItems;
    private Double grandTotal;
    private String status; // NEW, ACTIVE, CHECKING_OUT, CONVERTED
    private String last_activity_at;
    private String expires_at;
    private String created_at;
    private String updated_at;

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
        if (status == null || status.isBlank()) return false;
        if (totalItems == null || totalItems < 0) return false;
        if (grandTotal == null || grandTotal < 0) return false;
        if (lines == null) return false;
        for (CartLine l : lines) {
            if (l == null) return false;
            if (l.getSku() == null || l.getSku().isBlank()) return false;
            if (l.getQty() == null || l.getQty() <= 0) return false;
            if (l.getPrice() == null || l.getPrice() < 0) return false;
        }
        return true;
    }

    @Data
    public static class CartLine {
        private String sku;
        private String name;
        private Double price;
        private Integer qty;
        private Double lineTotal;

        public CartLine() {}
    }
}
