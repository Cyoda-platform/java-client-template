package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.util.List;

@Data
public class Mail implements CyodaEntity {
    private Boolean isHappy;
    private List<String> mailList;
    private String status; // Using String for status enum representation

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
        // Validate mailList is not null/empty and all entries are not blank
        if (mailList == null || mailList.isEmpty()) {
            return false;
        }
        for (String mail : mailList) {
            if (mail == null || mail.isBlank()) {
                return false;
            }
        }
        // isHappy must not be null
        if (isHappy == null) {
            return false;
        }
        // status can be null initially
        return true;
    }
}
