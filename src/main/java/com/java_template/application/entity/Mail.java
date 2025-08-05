package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.util.List;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Mail implements CyodaEntity {
    public static final String ENTITY_NAME = "Mail";

    private Boolean isHappy;
    private List<String> mailList;
    private String content;

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
        // Validate mailList is not null or empty
        if (mailList == null || mailList.isEmpty()) {
            return false;
        }
        // Validate each mail address is not blank
        for (String mail : mailList) {
            if (mail == null || mail.isBlank()) {
                return false;
            }
        }
        // Validate content is not blank
        if (content == null || content.isBlank()) {
            return false;
        }
        // isHappy can be null initially, so no validation on it
        return true;
    }
}
