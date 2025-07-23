package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.util.UUID;
import java.sql.Timestamp;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class DataIngestionJob implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID
    private String csvUrl;
    private String status; // Consider defining StatusEnum elsewhere
    private Timestamp createdAt;

    public DataIngestionJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("dataIngestionJob");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "dataIngestionJob");
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank()
            && csvUrl != null && !csvUrl.isBlank()
            && status != null && !status.isBlank();
    }
}
