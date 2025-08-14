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
        boolean itemsValid = items != null && !items.isEmpty();
        return cartId != null && !cartId.isBlank() &&
               customerId != null && !customerId.isBlank() &&
               itemsValid &&
               status != null && !status.isBlank();
    }
}
