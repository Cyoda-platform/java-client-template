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
    private String cartId;
    private String userId; // nullable
    private List<CartLine> lines;
    private Integer totalItems;
    private Double grandTotal;
    private String status; // NEW ACTIVE CHECKING_OUT CONVERTED
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
        return true;
    }

    @Data
    public static class CartLine {
        private String sku;
        private String name;
        private Double price;
        private Integer qty;
        private Double lineTotal;
    }
}
