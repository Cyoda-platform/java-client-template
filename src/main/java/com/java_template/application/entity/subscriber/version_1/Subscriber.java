package com.java_template.application.entity.subscriber.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class Subscriber implements CyodaEntity {
    public static final String ENTITY_NAME = Subscriber.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    private String id; // email address
    private String email;
    private String firstName;
    private String lastName;
    private LocalDateTime subscriptionDate;
    private Boolean isActive;
    private Map<String, Object> preferences; // preferredDay, timezone

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return email != null && !email.trim().isEmpty() && 
               email.contains("@") && email.contains(".");
    }
}
