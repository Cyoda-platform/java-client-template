package com.java_template.application.entity.transformedpet.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class TransformedPet implements CyodaEntity {
    public static final String ENTITY_NAME = "TransformedPet";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private Integer id; // business id
    private String name; // from petName
    private String species;
    private String breed;
    private Integer categoryId;
    private String availability; // friendly text
    private String age; // derived
    private String displayAttributes; // summary
    private String sourceMeta; // rawId + ingestedAt
    private String searchRequestId; // link (serialized UUID)

    // Added fields to support processors/criteria
    private String state; // e.g., TP_CREATED, VALIDATED, TP_FAILED, PUBLISHED, VIEWED, ARCHIVED
    private String createdAt; // timestamp when transformed pet was created
    private String technicalId; // serialized UUID for this entity

    public TransformedPet() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // require id and name and searchRequestId
        if (id == null || id <= 0) return false;
        if (name == null || name.isBlank()) return false;
        if (searchRequestId == null || searchRequestId.isBlank()) return false;
        return true;
    }
}
