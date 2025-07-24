package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class Mail implements CyodaEntity {
    private String id; // business ID
    private UUID technicalId; // database ID

    private Boolean isHappy;
    private Boolean isGloomy;
    private List<String> mailList;
    private Map<String, String> criteriaResults;
    private MailStatusEnum status;

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
        if (id == null || id.isBlank()) return false;
        if (technicalId == null) return false;
        if (mailList == null || mailList.isEmpty()) return false;
        if (status == null) return false;
        return true;
    }
}

enum MailStatusEnum {
    CREATED,
    PROCESSING,
    SENT_HAPPY,
    SENT_GLOOMY,
    FAILED
}
