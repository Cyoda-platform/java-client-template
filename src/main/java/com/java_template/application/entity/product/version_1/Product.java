package com.java_template.application.entity.product.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class Product implements CyodaEntity {
    public static final String ENTITY_NAME = "Product";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String sku; // stock keeping unit
    private String name;
    private String description;
    private BigDecimal price;
    private String currency;
    private Integer quantityAvailable;
    private Map<String, String> attributes; // free-form map
    private String importedAt; // ISO8601

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
        if (sku == null || sku.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (price == null) return false;
        if (price.compareTo(BigDecimal.ZERO) < 0) return false;
        if (currency == null || currency.isBlank()) return false;
        if (quantityAvailable == null || quantityAvailable < 0) return false;
        return true;
    }
}
