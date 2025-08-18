package com.java_template.application.entity.mailingList.version_1;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

import java.util.List;

@Data
public class MailingList implements CyodaEntity {
    public static final String ENTITY_NAME = "MailingList";
    public static final Integer ENTITY_VERSION = 1;

    private String id;
    private String technicalId;
    private String name;
    private List<String> recipients;
    private Boolean isActive;
    private String status;
    private String createdAt;
    private String updatedAt;

    public MailingList() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        return this.recipients != null;
    }
}
