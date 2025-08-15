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

    private String id; // business id / sku
    private String title; // product name
    private String description; // short description
    private Double price; // unit price
    private String currency; // currency code
    private Integer inventoryQuantity; // available stock
    private String status; // active discontinued pending_validation
    private String importedFrom; // ImportJob id or manual
    private String createdAt; // DateTime as ISO string

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
        if (title == null || title.isBlank()) return false;
        if (price == null || price < 0) return false;
        if (currency == null || currency.isBlank()) return false;
        return true;
    }
}
