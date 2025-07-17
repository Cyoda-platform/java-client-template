package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.util.List;
import java.util.UUID;

@Data
public class Pet implements CyodaEntity {
    private UUID technicalId;
    private String id; // legacy or external id
    private String name;
    private String category;
    private String status;
    private List<String> tags;
    private List<String> photoUrls;

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
        if (name == null || name.isBlank()) return false;
        if (category == null || category.isBlank() || category.length() > 50) return false;
        if (status == null || status.isBlank() || status.length() > 20) return false;
        if (tags != null) {
            if (tags.size() > 10) return false;
            for (String tag : tags) {
                if (tag == null || tag.isBlank()) return false;
            }
        }
        if (photoUrls != null) {
            if (photoUrls.size() > 10) return false;
            for (String photo : photoUrls) {
                if (photo == null || photo.isBlank()) return false;
            }
        }
        return true;
    }
}}