package com.java_template.application.entity.catfact.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CatFact implements CyodaEntity {
    public static final String ENTITY_NAME = "CatFact";
    public static final Integer ENTITY_VERSION = 1;

    // Add your entity fields here
    private String id; // business id (serialized UUID)
    private String fact_text; // the cat fact content
    private String source; // api/source identifier
    private String source_created_at; // original timestamp from source
    private String fetched_at; // when ingested
    private String language; // language code
    private List<String> tags; // array of tags
    private String curated_by; // user id if manually edited (serialized UUID)
    private String curated_at; // ISO timestamp
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
        // id and fact_text must be present and not blank
        if (id == null || id.isBlank()) return false;
        if (fact_text == null || fact_text.isBlank()) return false;
        // language if present should not be blank
        if (language != null && language.isBlank()) return false;
        return true;
    }
}
