package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

@Data
public class Pet implements CyodaEntity {
    private String id;
    private UUID technicalId;
    private String name;
    private String category;
    private List<String> photoUrls = new ArrayList<>();
    private List<String> tags = new ArrayList<>();
    private String status; // AVAILABLE, PENDING_ADOPTION, ADOPTED

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
        return id != null && !id.isBlank() && technicalId != null && name != null && !name.isBlank() && category != null && !category.isBlank() && status != null && !status.isBlank();
    }
}
