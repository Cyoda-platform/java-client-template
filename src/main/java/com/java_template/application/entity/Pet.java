package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class Pet implements CyodaEntity {
    private String id; // business ID
    private String name;
    private String category;
    private List<String> photoUrls;
    private List<String> tags;
    private PetStatusEnum status;
    private LocalDateTime createdAt;
    private UUID technicalId;

    public Pet() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("pet");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "pet");
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank() &&
               name != null && !name.isBlank() &&
               category != null && !category.isBlank() &&
               status != null &&
               createdAt != null;
    }

    public enum PetStatusEnum {
        AVAILABLE, PENDING, SOLD
    }
}
