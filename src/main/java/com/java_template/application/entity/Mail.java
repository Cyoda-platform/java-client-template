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
        if (this.mailList == null || this.mailList.isEmpty()) {
            return false;
        }
        if (this.content == null || this.content.isBlank()) {
            return false;
        }
        return true;
    }
}
