package com.java_template.application.entity.laureate.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Laureate implements CyodaEntity {
    public static final String ENTITY_NAME = "Laureate";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String affiliationCity;
    private String affiliationCountry;
    private String affiliationName;
    private Integer ageAtAward;
    private String awardYear;
    private String born;
    private String borncity;
    private String borncountry;
    private String borncountrycode;
    private String category;
    private String died;
    private String firstname;
    private String gender;
    private String laureateId;
    private String motivation;
    private String processingStatus;
    private Provenance provenance;
    private String surname;
    private List<String> validationErrors = new ArrayList<>();

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
        // Required string fields
        if (laureateId == null || laureateId.isBlank()) return false;
        if (awardYear == null || awardYear.isBlank()) return false;
        if (category == null || category.isBlank()) return false;
        if (processingStatus == null || processingStatus.isBlank()) return false;

        // Provenance must be present and its fields must be populated
        if (provenance == null) return false;
        if (provenance.getIngestionJobId() == null || provenance.getIngestionJobId().isBlank()) return false;
        if (provenance.getSourceRecordId() == null || provenance.getSourceRecordId().isBlank()) return false;
        if (provenance.getSourceTimestamp() == null || provenance.getSourceTimestamp().isBlank()) return false;

        // Numeric constraints
        if (ageAtAward != null && ageAtAward < 0) return false;

        return true;
    }

    @Data
    public static class Provenance {
        private String ingestionJobId;
        private String sourceRecordId;
        private String sourceTimestamp;
    }
}