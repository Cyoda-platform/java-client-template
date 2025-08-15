package com.java_template.application.entity.product.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;
import java.util.HashMap;

@Data
public class Product implements CyodaEntity {
    public static final String ENTITY_NAME = "Product";
    public static final Integer ENTITY_VERSION = 1;

    // Fields
    private String id; // UUID as string
    private String sku;
    private String name;
    private String description;
    private BigDecimal price;
    private String currency;
    private Integer stockQuantity;
    private Boolean active;
    private String createdAt; // ISO8601
    private String updatedAt; // ISO8601
    private Map<String, Object> metadata = new HashMap<>();
    private String importSource;
    private String importBatchId;

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
        if (sku == null || sku.isBlank()) return false;
        if (price == null) return false;
        if (price.compareTo(BigDecimal.ZERO) < 0) return false;
        if (stockQuantity == null) return false;
        if (stockQuantity < 0) return false;
        if (currency == null || currency.isBlank()) return false;
        return true;
    }
}
