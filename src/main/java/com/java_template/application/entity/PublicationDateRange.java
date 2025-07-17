package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class PublicationDateRange implements CyodaEntity {
    private String earliest;
    private String latest;

    public PublicationDateRange() {}

    public PublicationDateRange(String earliest, String latest) {
        this.earliest = earliest;
        this.latest = latest;
    }

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("publicationDateRange");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "publicationDateRange");
    }

    @Override
    public boolean isValid() {
        // Validation: earliest and latest should not be null
        return earliest != null && !earliest.isEmpty() && latest != null && !latest.isEmpty();
    }
}