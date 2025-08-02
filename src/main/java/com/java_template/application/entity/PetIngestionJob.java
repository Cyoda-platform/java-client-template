package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class PetIngestionJob implements CyodaEntity {
    public static final String ENTITY_NAME = "PetIngestionJob";
    private String status;
    private String startTime;
    private String endTime;
    private Integer ingestedPetCount;
    private String errorMessage;
    private String sourceApiUrl;
    private String targetPetStatus;

    public PetIngestionJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return status != null && !status.isBlank() &&
               sourceApiUrl != null && !sourceApiUrl.isBlank() &&
               targetPetStatus != null && !targetPetStatus.isBlank();
    }
}