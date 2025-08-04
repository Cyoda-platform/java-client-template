package com.java_template.application.processor;

import com.java_template.application.entity.WeeklyCatFactJob;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Component
public class WeeklyCatFactJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();

    public WeeklyCatFactJobProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing WeeklyCatFactJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(WeeklyCatFactJob.class)
                .validate(this::isValidEntity, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(WeeklyCatFactJob entity) {
        return entity != null && entity.isValid();
    }

    private WeeklyCatFactJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<WeeklyCatFactJob> context) {
        WeeklyCatFactJob entity = context.entity();

        // Business logic from CyodaEntityControllerPrototype for WeeklyCatFactJob
        // processWeeklyCatFactJob() logic implemented here
        // Initial state: status = PENDING, emailSentDate = null
        if (entity.getStatus() == null) {
            entity.setStatus("PENDING");
        }
        if (entity.getEmailSentDate() == null) {
            entity.setEmailSentDate(null);
        }

        // Note: Data ingestion, email preparation, publishing, and update job steps are
        // presumably handled by the workflow and other processors/events.
        // This processor represents the main job entity processing.

        // No additional fields to modify here since other entities (CatFact, Subscriber) are handled separately.

        return entity;
    }
}
