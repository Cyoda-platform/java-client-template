package com.java_template.application.entity.product.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Product implements CyodaEntity {
    public static final String ENTITY_NAME = "Product";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String productId; // business id
    private String name; // display name
    private String description; // product details
    private Double price; // unit price
    private String sku; // stock keeping unit
    private Integer stockQuantity; // available units
    private String status; // Active, Inactive, PendingValidation
    private String createdAt; // ISO timestamp

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
        if (sku == null || sku.isBlank()) return false;
        if (price == null || price <= 0) return false;
        if (stockQuantity == null || stockQuantity < 0) return false;
        return true;
    }
}
