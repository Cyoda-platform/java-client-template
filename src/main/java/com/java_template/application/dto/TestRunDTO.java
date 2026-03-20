package com.java_template.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.cyoda.cloud.api.event.common.ModelSpec;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Test Run DTO for TMS
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestRunDTO implements CyodaEntity {

    public static final String ENTITY_NAME = "TestRun";
    public static final Integer ENTITY_VERSION = 1;
    private static final ModelSpec MODEL_SPEC = new ModelSpec().withName(ENTITY_NAME).withVersion(ENTITY_VERSION);

    private UUID id;

    @NotNull(message = "Project ID is required")
    private UUID projectId;

    @JsonProperty("name")
    @NotBlank(message = "Test run name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String title;

    @Size(max = 100, message = "Environment must not exceed 100 characters")
    private String environment;

    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Override
    @JsonIgnore
    public OperationSpecification getModelKey() {
        return new OperationSpecification.Entity(MODEL_SPEC, ENTITY_NAME);
    }
}

