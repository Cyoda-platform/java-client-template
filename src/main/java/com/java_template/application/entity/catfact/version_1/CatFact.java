package com.java_template.application.entity.catfact.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class CatFact implements CyodaEntity {
    public static final String ENTITY_NAME = "CatFact";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id; // domain id for the fact
    private String text; // the cat fact content
    private String source; // source name or api endpoint
    private String retrieved_date; // ISO timestamp
    private String fact_date; // date provided by source if any
    private Boolean archived; // historical archive flag

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
        if (this.id == null || this.id.isBlank()) return false;
        if (this.text == null || this.text.isBlank()) return false;
        if (this.text.length() < 10) return false;
        if (this.source == null || this.source.isBlank()) return false;
        return true;
    }
}
