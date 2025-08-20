package com.java_template.application.entity.cart.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.util.List;
import java.time.OffsetDateTime;

@Data
public class Cart implements CyodaEntity {
    public static final String ENTITY_NAME = "Cart";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String cartId; // business id
    private String userId; // optional, links User (serialized UUID)
    private String status; // NEW -> ACTIVE -> CHECKING_OUT -> CONVERTED
    private List<CartLine> lines; // sku, name, price, qty
    private Integer totalItems;
    private Double grandTotal;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

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
        if (lines == null) return false;
        return true;
    }

    @Data
    public static class CartLine {
        private String sku;
        private String name;
        private Double price;
        private Integer qty;
    }
}
