package com.java_template.application.entity.product.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class Product implements CyodaEntity {
    public static final String ENTITY_NAME = "Product";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String product_id; // external product identifier from Pet Store API
    private String name; // product name
    private String category; // product category
    private Double price; // sale price
    private Double cost; // cost price
    private Integer stock_level; // current stock snapshot
    private String store_id; // store identifier (serialized UUID as String)
    private List<SalesSnapshot> sales_history; // time series of sales snapshots

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
        if (product_id == null || product_id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (category == null || category.isBlank()) return false;
        if (price == null || price < 0) return false;
        if (cost == null || cost < 0) return false;
        if (stock_level == null) return false;
        if (store_id == null || store_id.isBlank()) return false;
        return true;
    }
}

// SalesSnapshot is referenced by Product.sales_history; define in its own entity file as required.