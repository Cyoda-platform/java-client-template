package com.java_template.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.cyoda.cloud.api.event.common.ModelSpec;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Test Run Case DTO for TMS
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestRunCaseDTO implements CyodaEntity {

    public static final String ENTITY_NAME = "TestRunCase";
    public static final Integer ENTITY_VERSION = 1;
    private static final ModelSpec MODEL_SPEC = new ModelSpec().withName(ENTITY_NAME).withVersion(ENTITY_VERSION);

    private UUID id;

    @NotNull(message = "Test run ID is required")
    private UUID testRunId;

    @NotNull(message = "Test case ID is required")
    private UUID testCaseId;

    private String status;
    private String bugUrl;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @Override
    @JsonIgnore
    public OperationSpecification getModelKey() {
        return new OperationSpecification.Entity(MODEL_SPEC, ENTITY_NAME);
    }
}

