package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.util.UUID;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class PetDataRecord implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID
    private String jobId; // foreign key to DigestJob
    private Integer petId;
    private String name;
    private String category;
    private String status; // use String for status enum representation

    public PetDataRecord() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("petDataRecord");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "petDataRecord");
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank() && technicalId != null && jobId != null && !jobId.isBlank()
                && petId != null && name != null && !name.isBlank()
                && category != null && !category.isBlank()
                && status != null && !status.isBlank();
    }
}
