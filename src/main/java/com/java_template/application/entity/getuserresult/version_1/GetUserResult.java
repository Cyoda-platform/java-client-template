package com.java_template.application.entity.getuserresult.version_1;

import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;

@Data
public class GetUserResult implements CyodaEntity {
    public static final String ENTITY_NAME = "GetUserResult"; 
    public static final Integer ENTITY_VERSION = 1;
    // Add your entity fields here
    private String errorMessage;
    private String jobReference;
    private String retrievedAt;
    private String status; // use String for enum-like values
    private User user;

    public GetUserResult() {} 

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName(ENTITY_NAME);
        modelSpec.setVersion(ENTITY_VERSION);
        return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
    }

    @Override
    public boolean isValid() {
        if (jobReference == null || jobReference.isBlank()) return false;
        if (retrievedAt == null || retrievedAt.isBlank()) return false;
        if (status == null || status.isBlank()) return false;
        if (user == null) return false;
        return user.isValid();
    }
}