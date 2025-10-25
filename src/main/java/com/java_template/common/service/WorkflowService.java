package com.java_template.common.service;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public interface WorkflowService {

    JsonNode exportWorkflows(
            @NotNull String entityName,
            @NotNull Integer modelVersion
    );

}

