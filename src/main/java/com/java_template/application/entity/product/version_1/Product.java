package com.java_template.application.entity.product.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.util.Parameters;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Product implements CyodaEntity {
    public static final String ENTITY_NAME = "Product";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String technicalId; // system id
    private String productId; // id from source API
    private String name; // product title
    private String category; // product category
    private String sku; // stock keeping unit
    private Double price; // unit price
    private Integer stockLevel; // current inventory
    private Integer reorderPoint; // threshold for restock
    private List<Map<String, Object>> salesHistory; // time series entries: {date, unitsSold, revenue}
    private Map<String, Object> metrics; // computed KPIs: salesVolume, revenue, turnoverRate, lastPeriodComparison
    private List<String> flags; // arbitrary flags applied by processors
    private String lastUpdated; // timestamp of last update (ISO string)
    private String createdAt; // optional created timestamp
    private String status; // optional status

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
        if (category == null || category.isBlank()) return false;
        if (sku == null || sku.isBlank()) return false;
        if (price == null || price < 0) return false;
        if (stockLevel == null || stockLevel < 0) return false;
        if (lastUpdated == null || lastUpdated.isBlank()) return false;
        return true;
    }
}
