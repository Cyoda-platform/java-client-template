package com.java_template.application.entity.product.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class Product implements CyodaEntity {
    public static final String ENTITY_NAME = "Product";
    public static final Integer ENTITY_VERSION = 1;

    // Business fields
    private String id; // business identifier, e.g., SKU or external id
    private String name; // product display name
    private String description; // long description
    private BigDecimal price; // unit price
    private String currency; // ISO currency code for price
    private Integer stockQuantity; // available quantity for sale
    private Integer reservedQuantity; // quantity currently reserved (optional)
    private String technicalId; // optional internal technical id (UUID)
    private String category; // product category or taxonomy
    private String imageUrl; // URL to product image
    private String importedFrom; // optional source of import
    private String importedAt; // ISO8601 timestamp when product was imported
    private String createdByUserId; // id of user who created/imported the product
    private String createdAt; // ISO8601 timestamp
    private String updatedAt; // ISO8601 timestamp
    private String status; // ACTIVE, ERROR, DRAFT, etc.

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
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) return false;
        if (currency == null || currency.isBlank()) return false;
        if (stockQuantity == null || stockQuantity < 0) return false;
        return true;
    }
}
