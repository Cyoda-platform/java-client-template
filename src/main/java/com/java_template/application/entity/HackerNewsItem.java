package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import java.time.Instant;
import java.util.UUID;

@Data
public class HackerNewsItem implements CyodaEntity {
    private UUID id; // business ID
    private UUID technicalId; // database ID
    private String content; // JSON content as string
    private Instant timestamp; // time when item was saved
    private String status; // VALIDATED or INVALID

    public HackerNewsItem() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("hackerNewsItem");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "hackerNewsItem");
    }

    @Override
    public boolean isValid() {
        // Validate that content is not blank and contains id and type fields
        if (content == null || content.isBlank()) {
            return false;
        }
        // Since content is JSON string, actual validation of fields will be elsewhere
        return true;
    }
}
