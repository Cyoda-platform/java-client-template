package com.java_template.common.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.jetbrains.annotations.NotNull;

public interface WorkflowService {

    JsonNode exportWorkflows(
            @NotNull String entityName,
            @NotNull Integer modelVersion
    );

}

