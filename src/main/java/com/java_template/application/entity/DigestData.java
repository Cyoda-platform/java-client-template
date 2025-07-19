package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.UUID;

@Data
public class DigestData implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID

    private String jobId; // reference to DigestRequestJob as serialized UUID string
    private String data;
    private StatusEnum status;

    public DigestData() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("digestData");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "digestData");
    }

    @Override
    public boolean isValid() {
        if (id == null || id.isBlank()) return false;
        if (jobId == null || jobId.isBlank()) return false;
        if (status == null) return false;
        return true;
    }

    public enum StatusEnum {
        RETRIEVED, PROCESSED
    }
}
