package com.java_template.application.entity.pet.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class Pet implements CyodaEntity {
    public static final String ENTITY_NAME = "Pet";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String technicalId; // system-assigned id (UUID)
    private String id; // business id (optional)
    private String name; // pet name
    private String species; // cat/dog/etc
    private String breed; // breed info
    private Integer age; // years
    private String gender; // M/F/other
    private String lifecycleState; // CREATED/VALIDATING/AVAILABLE/PENDING/ADOPTED/ARCHIVED
    private String status; // available/adopted/pending (backward compatible)
    private String description; // short description
    private List<String> images; // URLs
    private String healthSummary; // brief health notes
    private String createdAt; // ISO-8601
    private String updatedAt; // ISO-8601
    private String errorMessage; // optional error summary

    public Pet() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // business-level validity: name and species required; status and age must be present and sane; images list non-null
        if (name == null || name.isBlank()) return false;
        if (species == null || species.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (age == null || age < 0) return false;
        if (images == null) return false;
        return true;
    }
}
