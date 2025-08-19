package com.java_template.application.entity.subscriber.version_1; // replace {entityName} with actual entity name in lowercase

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;
import java.util.ArrayList;

@Data
public class Subscriber implements CyodaEntity {
    public static final String ENTITY_NAME = "Subscriber";
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here

    private String id; // internal id
    private String email; // recipient email
    private String name; // recipient name
    private List<String> subscribedJobs = new ArrayList<>(); // job ids or job patterns
    private String preferredFormat; // pdf | html | csv
    private String frequency; // immediate | daily | weekly
    private String status; // ACTIVE | UNSUBSCRIBED | BOUNCED
    private String createdAt; // datetime (ISO string)

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
        if (email == null || email.isBlank()) return false;
        if (name == null || name.isBlank()) return false;
        if (preferredFormat == null || preferredFormat.isBlank()) return false;
        if (frequency == null || frequency.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        return true;
    }
}
