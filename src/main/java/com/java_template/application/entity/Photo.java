package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Photo implements CyodaEntity {
    private String id;
    private String title;
    private String imageUrl;
    private String thumbnailUrl;

    public Photo() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("photo");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "photo");
    }

    @Override
    public boolean isValid() {
        return id != null && !id.isBlank() && title != null && !title.isBlank();
    }
}
