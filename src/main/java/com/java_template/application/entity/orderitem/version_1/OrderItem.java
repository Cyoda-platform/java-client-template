package com.java_template.application.entity.orderitem.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItem implements CyodaEntity {
    public static final String ENTITY_NAME = "OrderItem";
    public static final Integer ENTITY_VERSION = 1;

    private String productId;
    private Integer quantity;
    private BigDecimal price;

    public OrderItem() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return productId != null && !productId.isBlank() &&
               quantity != null && quantity > 0 &&
               price != null && price.compareTo(BigDecimal.ZERO) >= 0;
    }
}
