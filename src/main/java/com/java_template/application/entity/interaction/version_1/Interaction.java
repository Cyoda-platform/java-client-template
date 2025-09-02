package com.java_template.application.entity.interaction.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class Interaction implements CyodaEntity {
    public static final String ENTITY_NAME = Interaction.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    private String id;
    private String subscriberId;
    private String catFactId;
    private String campaignId;
    private String interactionType; // EMAIL_OPENED, EMAIL_CLICKED, UNSUBSCRIBED
    private LocalDateTime interactionDate;
    private Map<String, Object> metadata; // userAgent, ipAddress, clickedLink

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return subscriberId != null && !subscriberId.trim().isEmpty() &&
               catFactId != null && !catFactId.trim().isEmpty() &&
               campaignId != null && !campaignId.trim().isEmpty() &&
               interactionType != null && !interactionType.trim().isEmpty() &&
               interactionDate != null && !interactionDate.isAfter(LocalDateTime.now());
    }
}
