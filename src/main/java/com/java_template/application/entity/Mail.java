package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.util.List;
import java.util.UUID;

@Data
public class Mail implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID

    private Boolean isHappy;
    private List<String> mailList;
    private String content;
    private String status; // Using String instead of enum for status

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
        if (id == null || id.isBlank()) {
            return false;
        }
        if (technicalId == null) {
            return false;
        }
        if (isHappy == null) {
            return false;
        }
        if (mailList == null || mailList.isEmpty()) {
            return false;
        }
        for (String mail : mailList) {
            if (mail == null || mail.isBlank()) {
                return false;
            }
        }
        if (content == null || content.isBlank()) {
            return false;
        }
        if (status == null || status.isBlank()) {
            return false;
        }
        return true;
    }
}
