package com.java_template.application.entity.storeditem.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class StoredItem implements CyodaEntity {
    public static final String ENTITY_NAME = "StoredItem"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    // hn_item stored as serialized JSON string
    private String hnItem;
    private Integer sizeBytes;
    // storage technical id (serialized UUID or technical identifier)
    private String storageTechnicalId;
    // ISO-8601 timestamp when stored
    private String storedAt;

    public StoredItem() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (hnItem == null || hnItem.isBlank()) return false;
        if (sizeBytes == null || sizeBytes < 0) return false;
        if (storageTechnicalId == null || storageTechnicalId.isBlank()) return false;
        if (storedAt == null || storedAt.isBlank()) return false;
        return true;
    }
}