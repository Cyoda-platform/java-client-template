package com.java_template.application.entity.mail.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class Mail implements CyodaEntity {
    public static final String ENTITY_NAME = "Mail"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    // Technical id (serialized UUID)
    private String id;
    private Boolean isHappy;
    private List<String> mailList;

    public Mail() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        // id is optional for creation, but if present it must not be blank
        if (id != null && id.isBlank()) {
            return false;
        }
        // isHappy must be provided
        if (isHappy == null) {
            return false;
        }
        // mailList must be provided and contain non-blank emails
        if (mailList == null || mailList.isEmpty()) {
            return false;
        }
        for (String m : mailList) {
            if (m == null || m.isBlank()) {
                return false;
            }
        }
        return true;
    }
}