package com.java_template.application.entity.product.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Product Entity for Product Management System
 * 
 * This entity represents a product in the system with inventory tracking
 * and pricing information.
 */
@Data
public class Product implements CyodaEntity {
    public static final String ENTITY_NAME = Product.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Required business identifier field
    private String productId;
    
    // Required core business fields
    private String name;
    private BigDecimal price;
    private String currency;
    private Integer inventory;
    
    // Optional fields
    private String description;
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid(org.cyoda.cloud.api.event.common.EntityMetadata metadata) {
        // Validate required fields
        return productId != null && !productId.trim().isEmpty() &&
               name != null && !name.trim().isEmpty() &&
               price != null && price.compareTo(BigDecimal.ZERO) >= 0 &&
               currency != null && !currency.trim().isEmpty() &&
               inventory != null && inventory >= 0;
    }
}

