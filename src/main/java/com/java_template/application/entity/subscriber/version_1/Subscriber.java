package com.java_template.application.entity.subscriber.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Subscriber implements CyodaEntity {
    public static final String ENTITY_NAME = "Subscriber";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private Long id; // business id
    private String technicalId; // datastore-imitation technical identifier returned by POST endpoints
    private String contactType; // email, webhook, etc.
    private String contactDetails; // email address or webhook URL or other contact payload
    private Boolean active; // is subscriber active
    private String preferences; // JSON blob with subscriber preferences e.g., notifyOnSuccess, notifyOnFailure, filters
    private String lastNotifiedAt; // ISO-8601 datetime

    // New fields referenced by processors
    private String lastNotificationStatus; // DELIVERED or FAILED

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
        // technicalId is required for persisted subscribers
        if (this.technicalId == null || this.technicalId.isBlank()) return false;
        // contactType and contactDetails required
        if (this.contactType == null || this.contactType.isBlank()) return false;
        if (this.contactDetails == null || this.contactDetails.isBlank()) return false;
        return true;
    }
}
