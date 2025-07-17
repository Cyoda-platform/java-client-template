package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.util.List;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Pet implements CyodaEntity {
    @NotBlank(message = "id is required")
    private String id;
    @NotBlank(message = "name is required")
    private String name;
    @NotBlank(message = "category is required")
    private String category;
    @Size(min = 1, message = "at least one photoUrl is required")
    private List<String> photoUrls;
    @Size(min = 1, message = "at least one tag is required")
    private List<String> tags;
    @NotBlank(message = "status is required")
    @Pattern(regexp = "(?i)available|pending|sold", message = "status must be 'available', 'pending', or 'sold'")
    private String status;

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
        if (id == null || id.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (category == null || category.isBlank()) return false;
        if (photoUrls == null || photoUrls.isEmpty()) return false;
        if (tags == null || tags.isEmpty()) return false;
        if (status == null || status.isBlank()) return false;
        if (!status.matches("(?i)available|pending|sold")) return false;
        return true;
    }
}}