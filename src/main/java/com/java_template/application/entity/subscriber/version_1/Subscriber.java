package com.java_template.application.entity.subscriber.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class Subscriber implements CyodaEntity {
    public static final String ENTITY_NAME = "Subscriber";
    public static final Integer ENTITY_VERSION = 1;

    private String contactType;
    private String contactAddress;
    private String subscribedCategories;
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
        if (contactType == null || contactType.isBlank()) return false;
        if (contactAddress == null || contactAddress.isBlank()) return false;
        // subscribedCategories can be blank or null but preferred not empty
        if (subscribedCategories == null || subscribedCategories.isBlank()) return false;
        if (active == null) return false;
        return true;
    }
}
