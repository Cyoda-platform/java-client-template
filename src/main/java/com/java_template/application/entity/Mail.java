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
    private String status; // Use String for MailStatusEnum

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
        if (mailList == null || mailList.isEmpty()) {
            return false;
        }
        // No need to check isHappy (Boolean) for blank, just allow null or Boolean
        // status should not be blank
        if (status == null || status.isBlank()) {
            return false;
        }
        return true;
    }
}
