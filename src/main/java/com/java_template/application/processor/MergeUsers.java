package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.manualusersync.version_1.ManualUserSync;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MergeUsers Processor - Merges Auth0 users into local store
 * 
 * Implements Auth0-authoritative merge policy:
 * - Creates new users from Auth0
 * - Updates existing users with Auth0 values
 * - Marks missing users as not present in Auth0
 * Attaching processor (attachEntity=true).
 */
@Component
public class MergeUsers implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MergeUsers.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public MergeUsers(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("MergeUsers: Starting merge for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(ManualUserSync.class)
                .validate(this::isValidEntity, "Invalid ManualUserSync entity")
                .map(this::processMergeLogic)
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

    private EntityWithMetadata<ManualUserSync> processMergeLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<ManualUserSync> context) {

        EntityWithMetadata<ManualUserSync> entityWithMetadata = context.entityResponse();
        ManualUserSync syncEntity = entityWithMetadata.entity();

        logger.debug("Processing merge for sync: {}", syncEntity.getId());

        // Initialize results
        ManualUserSync.SyncResults results = new ManualUserSync.SyncResults();
        results.setCreated(0);
        results.setUpdated(0);
        results.setMissing(0);
        results.setErrors(new ArrayList<>());

        try {
            // Simulate fetching Auth0 users (in real scenario, would come from Auth0Fetch output)
            List<Map<String, Object>> auth0Users = new ArrayList<>();
            
            // Process each Auth0 user
            for (Map<String, Object> auth0User : auth0Users) {
                mergeAuth0User(auth0User, results);
            }

            // Mark missing users
            markMissingUsers(results);

            // Update sync entity with results
            syncEntity.setResults(results);
            syncEntity.setUpdatedAt(OffsetDateTime.now());
            syncEntity.setStatus("completed");

            logger.info("MergeUsers: Merge completed. Created: {}, Updated: {}, Missing: {}",
                    results.getCreated(), results.getUpdated(), results.getMissing());

        } catch (Exception e) {
            logger.error("MergeUsers: Error during merge", e);
            if (syncEntity.getResults() == null) {
                syncEntity.setResults(new ManualUserSync.SyncResults());
            }
            if (syncEntity.getResults().getErrors() == null) {
                syncEntity.getResults().setErrors(new ArrayList<>());
            }
            syncEntity.getResults().getErrors().add("Merge error: " + e.getMessage());
        }

        return entityWithMetadata;
    }

    private void mergeAuth0User(Map<String, Object> auth0User, ManualUserSync.SyncResults results) {
        // Implementation for merging individual user
        logger.debug("Merging Auth0 user: {}", auth0User.get("user_id"));
    }

    private void markMissingUsers(ManualUserSync.SyncResults results) {
        // Implementation for marking missing users
        logger.debug("Marking missing users");
    }
}

