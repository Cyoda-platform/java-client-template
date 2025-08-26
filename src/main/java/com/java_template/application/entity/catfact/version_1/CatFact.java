package com.java_template.application.entity.catfact.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class CatFact implements CyodaEntity {
    public static final String ENTITY_NAME = "CatFact"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private OffsetDateTime archivedDate; // nullable
    private String factId;
    private OffsetDateTime fetchedDate;
    private String text;
    private String validationStatus; // use String for enums

    public CatFact() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // factId and text and fetchedDate and validationStatus are required
        if (factId == null || factId.isBlank()) return false;
        if (text == null || text.isBlank()) return false;
        if (fetchedDate == null) return false;
        if (validationStatus == null || validationStatus.isBlank()) return false;
        return true;
    }
}