package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class DigestDataRecord implements CyodaEntity {
    private final String className = this.getClass().getSimpleName();

    private String jobTechnicalId; // reference to DigestRequestJob technicalId
    private String apiEndpoint; // external API endpoint used to fetch data
    private String responseData; // raw JSON/string data retrieved from external API
    private String fetchedAt; // timestamp of data retrieval

    public DigestDataRecord() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(className);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, className);
    }

    @Override
    public boolean isValid() {
        if (jobTechnicalId == null || jobTechnicalId.isBlank()) return false;
        if (apiEndpoint == null || apiEndpoint.isBlank()) return false;
        if (responseData == null || responseData.isBlank()) return false;
        if (fetchedAt == null || fetchedAt.isBlank()) return false;
        return true;
    }
}
