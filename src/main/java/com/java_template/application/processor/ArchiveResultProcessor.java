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
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(GetUserResult entity) {
        return entity != null && entity.isValid();
    }

    private GetUserResult processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<GetUserResult> context) {
        GetUserResult entity = context.entity();

        // Archive lifecycle transition:
        // - mark the result as archived and update retrievedAt timestamp to indicate archival time.
        // Note: do not perform any add/update/delete on this entity via EntityService; the entity state
        // will be persisted automatically by Cyoda as part of the workflow.
        try {
            logger.info("Archiving GetUserResult jobReference={}", entity.getJobReference());
            entity.setStatus("ARCHIVED");
            entity.setRetrievedAt(Instant.now().toString());
        } catch (Exception ex) {
            logger.error("Failed to archive GetUserResult jobReference={}: {}", entity.getJobReference(), ex.getMessage(), ex);
            // Preserve entity state; errors here will be visible in logs. Do not throw to avoid interrupting workflow.
        }

        return entity;
    }
}