package com.java_template.application.entity.shoppingcart.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;

@Data
public class ShoppingCart implements CyodaEntity {
    public static final String ENTITY_NAME = "ShoppingCart";
    public static final Integer ENTITY_VERSION = 1;

    private String id;
    private String userId; // serialized UUID
    private String customerId; // legacy alias
    private List<CartItem> items = new ArrayList<>();
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;
    private String currency;
    private String updatedAt;

    public ShoppingCart() {}

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
        if ((userId == null || userId.isBlank()) && (customerId == null || customerId.isBlank())) return false;
        if (items == null) return false;
        return true;
    }
}
