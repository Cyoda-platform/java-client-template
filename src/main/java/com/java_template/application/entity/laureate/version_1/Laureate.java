package com.java_template.application.entity.laureate.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Map;

@Data
public class Laureate implements CyodaEntity {
    public static final String ENTITY_NAME = "Laureate";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String technicalId; // internal technical id (UUID string)
    private String laureateId; // canonical id assigned by system
    private String fullName; // person or organization name
    private Integer year; // award year
    private String category; // award category
    private String citation; // award citation text
    private String affiliations; // affiliations or institutions
    private String nationality; // country string
    private String sourceRecord; // raw payload or metadata JSON
    private String lifecycleStatus; // New/Updated/Unchanged
    private String matchTags; // computed keywords/categories
    private String createdAt; // timestamp
    private String updatedAt; // timestamp
    private Integer version; // change version number
    private Map<String, Object> provenance; // metadata map for notifications/delivery

    public Laureate() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (this.laureateId == null || this.laureateId.isBlank()) return false;
        if (this.fullName == null || this.fullName.isBlank()) return false;
        if (this.year == null) return false;
        // other fields may be optional
        return true;
    }
}
