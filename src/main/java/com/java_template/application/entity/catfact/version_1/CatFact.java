package com.java_template.application.entity.catfact.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class CatFact implements CyodaEntity {
    public static final String ENTITY_NAME = "CatFact";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id; // business id
    private String factText; // the cat fact content
    private String source; // api/source identifier
    private String sourceCreatedAt; // original timestamp from source
    private String fetchedAt; // when ingested
    private String language;
    private List<String> tags;
    private String curatedBy; // user id if manually edited
    private String curatedAt; // ISO timestamp
    private String status; // active/archived

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
        return id != null && !id.isBlank()
            && factText != null && !factText.isBlank()
            && language != null && !language.isBlank();
    }
}
