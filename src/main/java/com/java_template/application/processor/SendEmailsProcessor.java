package com.java_template.application.processor;

import com.java_template.application.entity.weeklysendjob.version_1.WeeklySendJob;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class SendEmailsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendEmailsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public SendEmailsProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing WeeklySendJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(WeeklySendJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(WeeklySendJob entity) {
        return entity != null && entity.isValid();
    }

    private WeeklySendJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<WeeklySendJob> context) {
        WeeklySendJob entity = context.entity();

        try {
            // Build condition to fetch ACTIVE subscribers
            SearchConditionRequest condition = SearchConditionRequest.group(
                "AND",
                Condition.of("$.status", "EQUALS", "ACTIVE")
            );

            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                condition,
                true
            );

            ArrayNode items = itemsFuture.get();
            List<Subscriber> activeSubscribers = new ArrayList<>();

            if (items != null) {
                for (int i = 0; i < items.size(); i++) {
                    ObjectNode node = (ObjectNode) items.get(i);
                    try {
                        Subscriber s = objectMapper.treeToValue(node, Subscriber.class);
                        if (s != null) {
                            activeSubscribers.add(s);
                        }
                    } catch (Exception ex) {
                        logger.warn("Failed to deserialize subscriber item at index {}: {}", i, ex.getMessage());
                    }
                }
            }

            // Update job target count
            entity.setTargetCount(activeSubscribers.size());

            // Simulate sending emails (logging only)
            for (Subscriber s : activeSubscribers) {
                if (s.getEmail() != null && !s.getEmail().isBlank()) {
                    // In real implementation, call an email service. Here we log the action.
                    logger.info("Sending catfact {} to subscriber: {} ({})", entity.getCatfactRef(), s.getEmail(), s.getId());
                } else {
                    logger.warn("Skipping subscriber with missing email: {}", s.getId());
                }
            }

            // Mark job completed
            entity.setStatus("COMPLETED");
            logger.info("WeeklySendJob {} completed. Sent to {} subscribers.", entity.getJobName(), entity.getTargetCount());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("SendEmailsProcessor interrupted: {}", ie.getMessage());
            entity.setStatus("FAILED");
        } catch (ExecutionException ee) {
            logger.error("Error fetching subscribers: {}", ee.getMessage());
            entity.setStatus("FAILED");
        } catch (Exception e) {
            logger.error("Unexpected error in SendEmailsProcessor: {}", e.getMessage(), e);
            entity.setStatus("FAILED");
        }

        return entity;
    }
}