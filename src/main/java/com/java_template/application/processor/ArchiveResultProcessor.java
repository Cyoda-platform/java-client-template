package com.java_template.application.processor;

import com.java_template.application.entity.getuserresult.version_1.GetUserResult;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@Component
public class ArchiveResultProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveResultProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ArchiveResultProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing GetUserResult for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(GetUserResult.class)
            .validate(this::isValidEntity, "Invalid GetUserResult: missing required fields")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Custom validation for archive processor:
     * - entity must be present
     * - jobReference and status must be present (allow user to be null because NOT_FOUND/ERROR results are valid)
     */
    private boolean isValidEntity(GetUserResult entity) {
        if (entity == null) return false;
        if (entity.getJobReference() == null || entity.getJobReference().isBlank()) return false;
        if (entity.getStatus() == null || entity.getStatus().isBlank()) return false;
        return true;
    }

    private GetUserResult processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<GetUserResult> context) {
        GetUserResult entity = context.entity();

        // Archive lifecycle transition:
        // - Only perform archive when the result is in DELIVERED state.
        // - mark the result as ARCHIVED and update retrievedAt timestamp to indicate archival time.
        // Note: do not perform any add/update/delete on this entity via EntityService; the entity state
        // will be persisted automatically by Cyoda as part of the workflow.
        try {
            String currentStatus = entity.getStatus();
            logger.info("ArchiveResultProcessor invoked for jobReference={} currentStatus={}", entity.getJobReference(), currentStatus);

            if (currentStatus != null && "DELIVERED".equalsIgnoreCase(currentStatus.trim())) {
                logger.info("Archiving GetUserResult jobReference={}", entity.getJobReference());
                entity.setStatus("ARCHIVED");
                entity.setRetrievedAt(Instant.now().toString());
            } else {
                logger.info("Skipping archive for jobReference={} because status is not DELIVERED (status={})", entity.getJobReference(), currentStatus);
            }
        } catch (Exception ex) {
            logger.error("Failed to archive GetUserResult jobReference={}: {}", entity.getJobReference(), ex.getMessage(), ex);
            // Preserve entity state; errors here will be visible in logs. Do not throw to avoid interrupting workflow.
        }

        return entity;
    }
}