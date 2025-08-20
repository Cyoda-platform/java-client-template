package com.java_template.application.entity.product.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Product implements CyodaEntity {
    public static final String ENTITY_NAME = "Product";
    public static final Integer ENTITY_VERSION = 1;

    private String productId; // business id / SKU
    private String name; // display name
    private String description; // marketing text
    private Double price; // unit price
    private Integer inventory; // available quantity
    private Boolean active; // sellable

    public Product() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (productId == null || productId.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (price == null || price < 0) return false;
        if (inventory == null || inventory < 0) return false;
        if (active == null) return false;
        return true;
    }
}
