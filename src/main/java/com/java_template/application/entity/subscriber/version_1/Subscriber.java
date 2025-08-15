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

    private String technicalId; // internal technical id (UUID string)
    private String name; // subscriber name
    private String contactMethod; // email/webhook/sms
    private String contactAddress; // destination address or endpoint
    private Boolean active; // is subscription active
    private String filters; // categories/keywords/year range expression (stored as JSON string)
    private String deliveryPreference; // immediate/digest/daily
    private String backfillFromDate; // optional date to backfill historical matches
    private String lastNotifiedAt; // timestamp
    private String notificationHistory; // reference to recent notifications
    private String createdAt; // timestamp
    private String updatedAt; // timestamp

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
        if (this.name == null || this.name.isBlank()) return false;
        if (this.contactMethod == null || this.contactMethod.isBlank()) return false;
        if (this.contactAddress == null || this.contactAddress.isBlank()) return false;
        if (this.filters == null || this.filters.isBlank()) return false;
        if (this.deliveryPreference == null || this.deliveryPreference.isBlank()) return false;
        return true;
    }
}
