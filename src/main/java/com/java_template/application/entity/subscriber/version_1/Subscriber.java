package com.java_template.application.entity.subscriber.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class Subscriber implements CyodaEntity {
    public static final String ENTITY_NAME = "Subscriber";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id; // business id
    private String contact; // email or webhook url or in-app id
    private Map<String, String> filters; // year, category, country, newOnly, updatedOnly
    private List<String> channels; // email, webhook, in-app
    private String status; // pending, verified, active, paused, unsubscribed
    private Map<String, Integer> retryPolicy; // attempts, interval
    private String createdAt; // timestamp
    private String lastNotifiedAt; // timestamp

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
        // Required: contact and channels
        if (this.contact == null || this.contact.isBlank()) return false;
        if (this.channels == null || this.channels.isEmpty()) return false;
        return true;
    }
}
