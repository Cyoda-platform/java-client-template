package com.java_template.application.entity.rawpet.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class RawPet implements CyodaEntity {
    public static final String ENTITY_NAME = "RawPet";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String rawId; // source id
    private String payload; // original JSON from external API
    private String species;
    private String status;
    private Integer categoryId;
    private String ingestedAt; // timestamp
    private String searchRequestId; // link (serialized UUID)
    private Boolean transformed; // flag

    public RawPet() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // rawId, payload and searchRequestId are required
        if (rawId == null || rawId.isBlank()) return false;
        if (payload == null || payload.isBlank()) return false;
        if (searchRequestId == null || searchRequestId.isBlank()) return false;
        return true;
    }
}
