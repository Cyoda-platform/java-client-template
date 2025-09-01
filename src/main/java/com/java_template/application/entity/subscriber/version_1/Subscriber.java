package com.java_template.application.entity.subscriber.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class Subscriber implements CyodaEntity {
    public static final String ENTITY_NAME = "Subscriber"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String email;
    private Integer interactionsCount;
    private String name;
    private String status; // use String for enum-like values (e.g., PENDING_CONFIRMATION)
    private OffsetDateTime subscribedAt;

    public Subscriber() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // Validate string fields using isBlank()
        if (email == null || email.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (status == null || status.isBlank()) return false;

        // interactionsCount must be present and non-negative
        if (interactionsCount == null || interactionsCount < 0) return false;

        // subscribedAt must be present
        if (subscribedAt == null) return false;

        return true;
    }
}