package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import java.util.List;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Mail implements CyodaEntity {
    private Boolean isHappy;
    private List<String> mailList;
    private String content;
    private String status;

    public Mail() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("mail");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "mail");
    }

    @Override
    public boolean isValid() {
        // Validate mailList is not null or empty
        if (mailList == null || mailList.isEmpty()) {
            return false;
        }
        // Validate no blank emails in mailList
        for (String email : mailList) {
            if (email == null || email.isBlank()) {
                return false;
            }
        }
        // Validate content is not blank
        if (content == null || content.isBlank()) {
            return false;
        }
        // Validate status is not blank
        if (status == null || status.isBlank()) {
            return false;
        }
        return true;
    }
}
