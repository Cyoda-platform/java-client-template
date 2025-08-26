package com.java_template.application.entity.subscriber.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.Map;

@Data
public class Subscriber implements CyodaEntity {
    public static final String ENTITY_NAME = "Subscriber"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String id; // technical id (e.g., "sub_222")
    private Boolean active;
    private String createdAt; // ISO timestamp
    private String email;
    private String lastDeliveryStatus;
    private String name;
    private String optOutAt; // ISO timestamp or null
    private Map<String, String> preferences; // e.g., { "frequency": "daily", "reportType": "summary" }

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
        // Validate required string fields using isBlank()
        if (id == null || id.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (createdAt == null || createdAt.isBlank()) return false;
        // Validate boolean presence
        if (active == null) return false;
        // Optional string fields: if present, must not be blank
        if (lastDeliveryStatus != null && lastDeliveryStatus.isBlank()) return false;
        if (optOutAt != null && optOutAt.isBlank()) return false;
        // Validate preferences map entries if provided
        if (preferences != null) {
            for (Map.Entry<String, String> e : preferences.entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank()) return false;
                if (e.getValue() == null || e.getValue().isBlank()) return false;
            }
        }
        return true;
    }
}