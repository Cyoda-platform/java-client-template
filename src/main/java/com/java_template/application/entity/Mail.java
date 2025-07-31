package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.util.List;

@Data
public class Mail implements CyodaEntity {
    public static final String ENTITY_NAME = "Mail";

    private Boolean isHappy;
    private List<String> mailList;

    public Mail() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (isHappy == null) {
            return false;
        }
        if (mailList == null || mailList.isEmpty()) {
            return false;
        }
        for (String email : mailList) {
            if (email == null || email.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
