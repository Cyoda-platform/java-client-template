package com.java_template.application.entity.subscriber.version_1; // replace {entityName} with actual entity name in lowercase

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

    private String technicalId;
    private String name;
    private String contactType;
    private String contactAddress;
    private Boolean active;
    private Map<String, Object> filters;
    private Map<String, Object> retryPolicy;
    private String lastNotifiedAt;
    private String createdAt;
    private String updatedAt;

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
        if (name == null || name.isBlank()) return false;
        if (contactType == null || contactType.isBlank()) return false;
        if (contactAddress == null || contactAddress.isBlank()) return false;
        return true;
    }
}
