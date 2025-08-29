package com.java_template.application.entity.inventorysnapshot.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class InventorySnapshot implements CyodaEntity {
    public static final String ENTITY_NAME = "InventorySnapshot";
    public static final Integer ENTITY_VERSION = 1;

    // Business identifier for the snapshot (e.g., "snap-20250825-01")
    private String snapshotId;

    // Foreign key reference to Product (serialized UUID or product identifier)
    private String productId;

    // ISO-8601 timestamp string when the snapshot was taken
    private String snapshotAt;

    // Current stock level at snapshot time
    private Integer stockLevel;

    // Threshold at which restocking should occur
    private Integer restockThreshold;

    public InventorySnapshot() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate string fields using isBlank() to catch empty/whitespace-only values
        if (snapshotId == null || snapshotId.isBlank()) return false;
        if (productId == null || productId.isBlank()) return false;
        if (snapshotAt == null || snapshotAt.isBlank()) return false;

        // Validate numeric fields
        if (stockLevel == null || stockLevel < 0) return false;
        if (restockThreshold == null || restockThreshold < 0) return false;

        return true;
    }
}