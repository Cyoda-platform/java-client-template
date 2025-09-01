package com.java_template.application.entity.adoptionjob.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AdoptionJob implements CyodaEntity {
    public static final String ENTITY_NAME = "AdoptionJob";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String id; // technical id (serialized UUID or string)
    private String ownerId; // foreign key reference to Owner, serialized UUID as String
    private String criteria; // serialized criteria JSON
    private String status; // status as String (use String for enums)
    private String createdAt; // ISO-8601 timestamp as String
    private Integer resultCount = 0;
    private List<String> resultsPreview = new ArrayList<>();

    public AdoptionJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate required string fields using String.isBlank()
        if (id == null || id.isBlank()) return false;
        if (ownerId == null || ownerId.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (criteria == null || criteria.isBlank()) return false;

        // Validate numeric fields
        if (resultCount == null || resultCount < 0) return false;

        // Validate list fields
        if (resultsPreview == null) return false;
        for (String s : resultsPreview) {
            if (s == null || s.isBlank()) return false;
        }

        return true;
    }
}