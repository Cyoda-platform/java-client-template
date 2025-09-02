package com.java_template.application.entity.mail.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.ModelSpec;

import java.util.List;

@Data
public class Mail implements CyodaEntity {
    public static final String ENTITY_NAME = Mail.class.getSimpleName();
    public static final Integer ENTITY_VERSION = 1;

    private Boolean isHappy;
    private List<String> mailList;

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return isHappy != null && mailList != null && !mailList.isEmpty();
    }
}
