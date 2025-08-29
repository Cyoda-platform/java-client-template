package com.java_template.application.entity.salesrecord.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class SalesRecord implements CyodaEntity {
    public static final String ENTITY_NAME = "SalesRecord"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    // recordId is the technical id for the SalesRecord
    private String recordId;
    // ISO-8601 timestamp string when the sale occurred
    private String dateSold;
    // Foreign key reference to Product as serialized UUID (use String)
    private String productId;
    private Integer quantity;
    private Double revenue;
    // Raw source payload as received (JSON string)
    private String rawSource;

    public SalesRecord() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields using isBlank()
        if (recordId == null || recordId.isBlank()) return false;
        if (dateSold == null || dateSold.isBlank()) return false;
        if (productId == null || productId.isBlank()) return false;
        if (rawSource == null || rawSource.isBlank()) return false;
        // Validate numeric fields
        if (quantity == null || quantity <= 0) return false;
        if (revenue == null || revenue < 0.0) return false;
        return true;
    }
}