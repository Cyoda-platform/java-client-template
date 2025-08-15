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

    private String id; // business subscriber id
    private String name; // subscriber name or organization
    private String email; // contact email, optional
    private String webhookUrl; // HTTP endpoint for push notifications, optional
    private List<String> channels; // preferred channels: EMAIL, WEBHOOK
    private Boolean active; // whether notifications are enabled
    private Map<String,Object> filters; // subscriber-level filters
    private String createdAt; // ISO-8601 timestamp
    private String verifiedAt; // ISO-8601 timestamp when contact verified
    private String lastNotifiedAt; // ISO-8601 timestamp
    private Map<String,Object> retryPolicy; // e.g., maxRetries, backoffSeconds
    private Integer deliveryFailures; // number of consecutive delivery failures

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
        // Basic validation: required fields
        if (this.name == null || this.name.isBlank()) return false;
        if (this.channels == null || this.channels.isEmpty()) return false;
        if (this.active == null) return false;
        return true;
    }
}
