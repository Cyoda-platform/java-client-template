package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.List;

@Data
public class Pet implements CyodaEntity {
    public static final String ENTITY_NAME = "Pet";

    private Long id; // Petstore API pet identifier
    private String name;
    private Category category;
    private List<String> photoUrls;
    private List<Tag> tags;
    private String status; // e.g., "available", "pending", "sold"

    public Pet() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (name == null || name.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }

    @Data
    public static class Category {
        private Long id;
        private String name;

        public Category() {}
    }

    @Data
    public static class Tag {
        private Long id;
        private String name;

        public Tag() {}
    }
}
