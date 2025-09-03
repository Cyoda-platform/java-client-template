package com.java_template.application.entity.product.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class Product implements CyodaEntity {
    public static final String ENTITY_NAME = Product.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    // Unique identifier for the product (from Pet Store API)
    private Long id;
    
    // Product name (required)
    private String name;
    
    // Product category name
    private String category;
    
    // Category identifier
    private Long categoryId;
    
    // Product image URLs
    private List<String> photoUrls;
    
    // Product tags for categorization
    private List<String> tags;
    
    // Product price
    private BigDecimal price;
    
    // Current stock level
    private Integer stockQuantity;
    
    // Total units sold
    private Integer salesVolume;
    
    // Total revenue generated
    private BigDecimal revenue;
    
    // Date of last sale
    private LocalDateTime lastSaleDate;
    
    // Product creation timestamp
    private LocalDateTime createdAt;
    
    // Last update timestamp
    private LocalDateTime updatedAt;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return name != null && !name.trim().isEmpty() && 
               id != null && id > 0 &&
               photoUrls != null && !photoUrls.isEmpty();
    }
}
