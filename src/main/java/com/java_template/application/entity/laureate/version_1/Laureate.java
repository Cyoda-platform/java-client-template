package com.java_template.application.entity.laureate.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Laureate implements CyodaEntity {
    public static final String ENTITY_NAME = "Laureate";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id; // business id from source or generated
    private String fullName; // person name
    private String year; // award year
    private String category; // award category
    private String country; // affiliation country
    private String affiliation; // institution
    private String citation; // award citation
    private String sourceUrl; // original record url
    private Integer currentVersion; // incremental version
    private String lastUpdated; // timestamp
    private String changeType; // new or updated
    private Boolean archived; // soft delete/archive marker

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
        // Validate required fields: id, fullName, year, category
        if (this.id == null || this.id.isBlank()) return false;
        if (this.fullName == null || this.fullName.isBlank()) return false;
        if (this.year == null || this.year.isBlank()) return false;
        if (this.category == null || this.category.isBlank()) return false;
        return true;
    }
}
