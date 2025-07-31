package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

@Data
public class Job implements CyodaEntity {
    public static final String ENTITY_NAME = "Job";

    private String mailList; // List of email addresses to send mails to
    private Boolean isHappy; // Flag indicating whether to send a happy or gloomy mail
    private String content; // The content of the email to be sent
    private String status; // Current status of the job - PENDING, PROCESSING, COMPLETED, FAILED

    public Job() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
       return (mailList != null && !mailList.isBlank()) && (content != null && !content.isBlank());
    }
}
