package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.util.List;

@Data
public class Mail implements CyodaEntity {
    private final String className = this.getClass().getSimpleName();

    private Boolean isHappy;
    private List<String> mailList;
    private String content;
    private String status;

    public Mail() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(className);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, className);
    }

    @Override
    public boolean isValid() {
        if (mailList == null || mailList.isEmpty()) {
            return false;
        }
        if (content == null || content.isBlank()) {
            return false;
        }
        if (isHappy == null) {
            return false;
        }
        return true;
    }
}
