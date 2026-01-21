package com.java_template.application.processor;

import com.java_template.application.entity.manualusersync.version_1.ManualUserSync;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * PreflightCheck Processor - Validates Auth0 configuration before sync
 * 
 * This processor verifies that all required Auth0 credentials are configured
 * and accessible before attempting to fetch users from Auth0.
 * Attaching processor (attachEntity=true).
 */
@Component
public class PreflightCheck implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PreflightCheck.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    @Value("${auth0.domain:}")
    private String auth0Domain;

    @Value("${auth0.clientId:}")
    private String auth0ClientId;

    @Value("${auth0.clientSecret:}")
    private String auth0ClientSecret;

    @Value("${auth0.audience:}")
    private String auth0Audience;

    public PreflightCheck(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("PreflightCheck: Starting validation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(ManualUserSync.class)
                .validate(this::isValidEntity, "Invalid ManualUserSync entity")
                .map(this::performPreflightCheck)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(EntityWithMetadata<ManualUserSync> entityWithMetadata) {
        ManualUserSync entity = entityWithMetadata.entity();
        return entity != null && entity.getId() != null && !entity.getId().isBlank();
    }

    private EntityWithMetadata<ManualUserSync> performPreflightCheck(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<ManualUserSync> context) {

        EntityWithMetadata<ManualUserSync> entityWithMetadata = context.entityResponse();
        ManualUserSync syncEntity = entityWithMetadata.entity();

        logger.debug("Performing preflight checks for sync: {}", syncEntity.getId());

        // Validate Auth0 configuration
        if (!isAuth0Configured()) {
            logger.error("PreflightCheck: Auth0 configuration is incomplete");
            if (syncEntity.getResults() == null) {
                syncEntity.setResults(new ManualUserSync.SyncResults());
            }
            syncEntity.getResults().setErrors(java.util.List.of(
                "Auth0 configuration incomplete: domain, clientId, clientSecret, and audience are required"
            ));
            return entityWithMetadata;
        }

        logger.info("PreflightCheck: All validations passed for sync: {}", syncEntity.getId());
        syncEntity.setStatus("validated");
        syncEntity.setStartedAt(OffsetDateTime.now());

        return entityWithMetadata;
    }

    private boolean isAuth0Configured() {
        return auth0Domain != null && !auth0Domain.isBlank() &&
               auth0ClientId != null && !auth0ClientId.isBlank() &&
               auth0ClientSecret != null && !auth0ClientSecret.isBlank() &&
               auth0Audience != null && !auth0Audience.isBlank();
    }
}

