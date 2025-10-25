package com.java_template.common.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.auth.Authentication;
import com.java_template.common.exception.WorkflowExportException;
import com.java_template.common.util.HttpUtils;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import static com.java_template.common.config.Config.CYODA_API_URL;


@Service
public class WorkflowServiceImpl implements WorkflowService {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowServiceImpl.class);

    private final HttpUtils httpUtils;
    private final Authentication authentication;

    /**
     * Constructor for WorkflowServiceImpl with dependency injection.
     *
     * @param httpUtils HTTP utility component for making REST API calls
     * @param authentication OAuth2 authentication component for token management
     */
    public WorkflowServiceImpl(
            final HttpUtils httpUtils,
            final Authentication authentication
    ) {
        this.httpUtils = httpUtils;
        this.authentication = authentication;
    }

    @Override
    public JsonNode exportWorkflows(
            @NotNull final String entityName,
            @NotNull final Integer modelVersion
    ) {
        logger.debug("Exporting workflows for entity: {} (version: {})", entityName, modelVersion);

        try {
            // Get OAuth2 access token
            String token = authentication.getAccessToken().getTokenValue();

            // Construct API path: model/{entityName}/{modelVersion}/workflow/export
            String exportPath = String.format("model/%s/%d/workflow/export", entityName, modelVersion);
            logger.debug("Using export endpoint: {}", exportPath);

            // Make HTTP GET request to Cyoda API
            ObjectNode response = httpUtils.sendGetRequest(token, CYODA_API_URL, exportPath).join();
            int statusCode = response.get("status").asInt();

            // Check response status
            if (statusCode >= 200 && statusCode < 300) {
                logger.info("Successfully exported workflows for entity: {} (version: {})", entityName, modelVersion);
                return response.get("json");
            } else if (statusCode == 404) {
                String errorMsg = String.format(
                    "Entity model not found: %s (version %d). Please verify the entity name and version.",
                    entityName, modelVersion
                );
                throw new WorkflowExportException(errorMsg, statusCode);
            } else {
                String errorBody = response.path("json").toString();
                String errorMsg = String.format(
                    "Failed to export workflows for entity %s (version %d). Status code: %d, Response: %s",
                    entityName, modelVersion, statusCode, errorBody
                );
                throw new WorkflowExportException(errorMsg, statusCode);
            }
        } catch (WorkflowExportException e) {
            // Re-throw WorkflowExportException without logging (will be logged by controller)
            throw e;
        } catch (Exception e) {
            // Wrap unexpected exceptions
            String errorMsg = String.format(
                "Unexpected error exporting workflows for entity %s (version %d): %s",
                entityName, modelVersion, e.getMessage()
            );
            throw new WorkflowExportException(errorMsg, e);
        }
    }

}

