package com.java_template.application.entity;

import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import lombok.Data;
import static com.java_template.common.config.Config.ENTITY_VERSION;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

@Data
public class UserAppJob implements CyodaEntity {

    @NotBlank
    private String buildId;
    @NotBlank
    private String userName;
    @NotBlank
    private String repositoryUrl;
    private boolean isPublic;
    @NotBlank
    private String status;
    private Instant requestedAt;

    public UserAppJob() {}

    @Override
    public OperationSpecification getModelKey() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("userAppJob");
        modelSpec.setVersion(Integer.parseInt(ENTITY_VERSION));
        return new OperationSpecification.Entity(modelSpec, "userAppJob");
    }

    @Override
    public boolean isValid() {
        return buildId != null && !buildId.isEmpty()
            && userName != null && !userName.isEmpty()
            && repositoryUrl != null && !repositoryUrl.isEmpty()
            && status != null && !status.isEmpty();
    }
}
