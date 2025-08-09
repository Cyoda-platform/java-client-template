package com.java_template.application.entity.subscriber.version_1000;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Subscriber implements CyodaEntity {
    public static final String ENTITY_NAME = "Subscriber";
    public static final Integer ENTITY_VERSION = 1000;

    private String subscriberId;
    private String contactType;
    private String contactAddress;
    private Boolean active;

    public Subscriber() {}

    @Override
    @JsonIgnore
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    @JsonIgnore
    public boolean isValid() {
        if (subscriberId == null || subscriberId.isBlank()) return false;
        if (contactType == null || contactType.isBlank()) return false;
        if (contactAddress == null || contactAddress.isBlank()) return false;
        if (active == null) return false;
        return true;
    }
}
