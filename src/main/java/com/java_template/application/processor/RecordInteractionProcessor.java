package com.java_template.application.processor;

import com.java_template.application.entity.interaction.version_1.Interaction;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ABOUTME: Processor for recording user interactions with email campaigns.
 * Validates and processes interaction events (opens, clicks, etc.) for reporting.
 */
@Component
public class RecordInteractionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RecordInteractionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public RecordInteractionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing RecordInteraction for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Interaction.class)
                .validate(this::isValidEntityWithMetadata, "Invalid interaction wrapper")
                .map(this::processRecord)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Interaction> entityWithMetadata) {
        Interaction entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Interaction> processRecord(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Interaction> context) {

        EntityWithMetadata<Interaction> entityWithMetadata = context.entityResponse();
        Interaction interaction = entityWithMetadata.entity();

        logger.debug("Recording interaction: {} for subscriber: {}", 
                   interaction.getInteractionType(), interaction.getSubscriberId());

        // Validate interaction type
        String interactionType = interaction.getInteractionType().toLowerCase();
        if (!isValidInteractionType(interactionType)) {
            logger.warn("Unknown interaction type: {}", interaction.getInteractionType());
        }

        logger.info("Interaction {} recorded successfully for campaign {}", 
                   interaction.getInteractionId(), interaction.getCampaignId());

        return entityWithMetadata;
    }

    private boolean isValidInteractionType(String type) {
        return type.equals("open") || type.equals("click") || type.equals("unsubscribe");
    }
}

