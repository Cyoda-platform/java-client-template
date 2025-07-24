package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.util.List;
import java.util.UUID;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Pet implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID
    private String name; // pet's name
    private String category; // type of pet, e.g., cat, dog
    private List<String> photoUrls; // URLs of pet photos
    private List<String> tags; // tags/labels for the pet
    private String status; // AVAILABLE, PENDING, SOLD

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
        return id != null && !id.isBlank() && name != null && !name.isBlank() && category != null && !category.isBlank() && status != null && !status.isBlank();
    }
}
