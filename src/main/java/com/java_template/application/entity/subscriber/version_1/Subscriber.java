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
    private String id; // business id (serialized UUID)
    private String email; // subscriber email
    private String name; // display name
    private String timezone; // subscriber timezone for logging
    private String subscription_status; // active/paused/unsubscribed/archived
    private String signup_date; // ISO timestamp
    private Map<String, Object> preferences; // tags, other user prefs
    private String last_delivery_id; // reference to last delivery record (serialized UUID)

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
        // email and id must be present and not blank
        if (id == null || id.isBlank()) return false;
        if (email == null || email.isBlank()) return false;
        // timezone should not be blank if present
        if (timezone != null && timezone.isBlank()) return false;
        return true;
    }
}
