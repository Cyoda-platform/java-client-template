package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.time.LocalDateTime;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Subscription implements CyodaEntity {
    private String subscriptionId;
    private String userId;
    private String team;
    private NotificationTypeEnum notificationType;
    private NotificationChannelEnum channel;
    private SubscriptionStatusEnum status;
    private LocalDateTime createdAt;

    public Subscription() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("subscription");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "subscription");
    }

    @Override
    public boolean isValid() {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        if (channel == null) {
            return false;
        }
        if (status == null) {
            return false;
        }
        if (notificationType == null) {
            return false;
        }
        // team is nullable, no validation for team presence
        return true;
    }
}
