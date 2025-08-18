package com.java_template.application.entity.subscriber.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Subscriber implements CyodaEntity {
    public static final String ENTITY_NAME = "Subscriber";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id; // domain id for subscriber record
    private String email; // subscriber email address
    private String name; // optional display name
    private String status; // active / pending_confirmation / unsubscribed / bounced
    private String subscribed_date; // ISO timestamp
    private String unsubscribed_date; // ISO timestamp
    private Boolean consent_given; // opt-in flag
    private String last_interaction_date; // ISO timestamp

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
        if (this.id == null || this.id.isBlank()) return false;
        if (this.email == null || this.email.isBlank()) return false;
        if (!this.email.contains("@")) return false;
        if (this.status == null || this.status.isBlank()) return false;
        return true;
    }
}
