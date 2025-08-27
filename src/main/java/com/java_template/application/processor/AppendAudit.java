package com.java_template.application.processor;

import com.java_template.application.entity.audit.version_1.Audit;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class AppendAudit implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AppendAudit.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public AppendAudit(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Audit for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Audit.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Audit entity) {
        return entity != null && entity.isValid();
    }

    private Audit processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Audit> context) {
        Audit entity = context.entity();

        // Ensure immutable audit identifiers and timestamp are present
        if (entity.getAudit_id() == null || entity.getAudit_id().isBlank()) {
            entity.setAudit_id(UUID.randomUUID().toString());
        }

        if (entity.getTimestamp() == null || entity.getTimestamp().isBlank()) {
            entity.setTimestamp(Instant.now().toString());
        }

        // Ensure required actor and entity_ref are present (isValid already checked),
        // but log if something looks questionable.
        if (entity.getActor_id() == null || entity.getActor_id().isBlank()) {
            logger.warn("Appending audit without actor_id for audit_id={}", entity.getAudit_id());
        }
        if (entity.getEntity_ref() == null || entity.getEntity_ref().isBlank()) {
            logger.warn("Appending audit without entity_ref for audit_id={}", entity.getAudit_id());
        }

        // Persist the Audit as a separate entity using EntityService.
        try {
            CompletableFuture<java.util.UUID> idFuture = entityService.addItem(
                Audit.ENTITY_NAME,
                String.valueOf(Audit.ENTITY_VERSION),
                entity
            );
            java.util.UUID persistedId = idFuture.get();
            logger.info("Appended Audit persisted with technical id: {}", persistedId);
        } catch (Exception e) {
            logger.error("Failed to persist Audit entity for audit_id={}", entity.getAudit_id(), e);
            // Do not throw; allow workflow to continue. The audit may be retried by infrastructure if needed.
        }

        return entity;
    }
}