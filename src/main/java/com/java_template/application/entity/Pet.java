package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.util.List;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Pet implements CyodaEntity {
    private Long petId; // ID from Petstore API
    private String name; // pet name
    private String category; // pet category name
    private List<String> photoUrls; // photos of the pet
    private List<String> tags; // tags associated with pet
    private String status; // pet status from Petstore (available, pending, sold)
    private String ingestedAt; // ISO timestamp when pet data was ingested

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
        if (petId == null) {
            return false;
        }
        if (name == null || name.isBlank()) {
            return false;
        }
        if (status == null || status.isBlank()) {
            return false;
        }
        return true;
    }
}
