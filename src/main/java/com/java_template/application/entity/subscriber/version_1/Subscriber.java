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
    private String subscriberId;
    private String name;
    private String contactEmail;
    private String deliveryPreference;
    private String webhookUrl;
    private Boolean active;

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
        // Basic validations: required string fields must be non-null and not blank.
        if (subscriberId == null || subscriberId.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (contactEmail == null || contactEmail.isBlank()) return false;
        if (deliveryPreference == null || deliveryPreference.isBlank()) return false;
        if (active == null) return false;
        // If delivery preference is webhook, webhookUrl must be provided
        if ("webhook".equalsIgnoreCase(deliveryPreference) && (webhookUrl == null || webhookUrl.isBlank())) return false;
        return true;
    }
}