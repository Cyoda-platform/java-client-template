package com.java_template.application.entity.emailcampaign.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.time.LocalDateTime;

@Data
public class EmailCampaign implements CyodaEntity {
    public static final String ENTITY_NAME = EmailCampaign.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    private String id;
    private String campaignName;
    private String catFactId;
    private LocalDateTime scheduledDate;
    private LocalDateTime sentDate;
    private Integer totalSubscribers;
    private Integer successfulSends;
    private Integer failedSends;
    private String subject;
    private String emailTemplate;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return campaignName != null && !campaignName.trim().isEmpty() &&
               catFactId != null && !catFactId.trim().isEmpty() &&
               scheduledDate != null &&
               totalSubscribers != null && totalSubscribers >= 0 &&
               (successfulSends == null || successfulSends >= 0) &&
               (failedSends == null || failedSends >= 0);
    }
}
