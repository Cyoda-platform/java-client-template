package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.JsonUtils;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class SendConfirmationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendConfirmationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final JsonUtils jsonUtils;

    public SendConfirmationProcessor(SerializerFactory serializerFactory, EntityService entityService, JsonUtils jsonUtils) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.jsonUtils = jsonUtils;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Subscriber entity) {
        return entity != null && entity.isValid();
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber entity = context.entity();
        try {
            // If preferences indicate confirmation required or system default requires it, send a confirmation
            boolean requiresConfirmation = true; // default true for system; in future, read from config or entity.preferences

            if (requiresConfirmation) {
                // Set status to pending and confirmed=false if not already
                entity.setStatus("pending");
                entity.setConfirmed(false);

                // Create a confirmation token record (as a simple UUID) persisted somewhere; here we attach to preferences as a TODO
                String token = UUID.randomUUID().toString();
                ObjectNode prefNode = jsonUtils.parseToObjectNode(entity.getPreferences());
                if (prefNode == null) {
                    prefNode = jsonUtils.createObjectNode();
                }
                prefNode.put("confirmation_token", token);
                prefNode.put("confirmation_sent_at", Instant.now().toString());
                entity.setPreferences(jsonUtils.toJson(prefNode));

                // TODO: enqueue to email sending system. For now we log the token and rely on SendEmailNotificationProcessor for sending mechanisms.
                logger.info("Confirmation token generated for {} : {}", entity.getEmail(), token);
            } else {
                // Auto-activate
                entity.setStatus("active");
                entity.setConfirmed(true);
            }

            // Persist changes via entityService: updateItem requires technicalId; however processors are executed in reaction to persistence. We modify entity state only.
        } catch (Exception e) {
            logger.error("Error in SendConfirmationProcessor: {}", e.getMessage(), e);
        }
        return entity;
    }
}
