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
    private String productId; // serialized UUID or technical id
    private String name;
    private String category;
    private String performanceFlag; // enum represented as String
    private String lastUpdated; // ISO timestamp as String

    private Double price;
    private Double cost;
    private Double totalRevenue;

    private Integer inventoryOnHand;
    private Integer totalSalesVolume;

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
        // Required string fields
        if (productId == null || productId.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (category == null || category.isBlank()) return false;
        if (lastUpdated == null || lastUpdated.isBlank()) return false;

        // Numeric validations
        if (price == null || price < 0.0) return false;
        if (cost == null || cost < 0.0) return false;
        if (totalRevenue == null || totalRevenue < 0.0) return false;

        if (inventoryOnHand == null || inventoryOnHand < 0) return false;
        if (totalSalesVolume == null || totalSalesVolume < 0) return false;

        // performanceFlag can be optional/blank, no strict check here

        return true;
    }
}