package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class ProductData implements CyodaEntity {
    private String productId;
    private String name;
    private String category;
    private Integer salesVolume;
    private Double revenue;
    private Integer inventoryCount;

    public ProductData() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("productData");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "productData");
    }

    @Override
    public boolean isValid() {
        if (productId == null || productId.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (category == null || category.isBlank()) return false;
        if (salesVolume == null) return false;
        if (revenue == null) return false;
        if (inventoryCount == null) return false;
        return true;
    }
}
