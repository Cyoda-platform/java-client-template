package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.delivery.version_1.Delivery;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.application.entity.catfact.version_1.CatFact;
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
import java.util.Map;

@Component
public class SendEmailProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendEmailProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    private static final int DEFAULT_MAX_RETRIES = 2;
    private final EntityService entityService;
    private final ObjectMapper mapper;

    public SendEmailProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper mapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.mapper = mapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SendEmail for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Delivery.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Delivery entity) {
        return entity != null && entity.isValid();
    }

    private Delivery processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Delivery> context) {
        Delivery delivery = context.entity();
        try {
            // Load latest delivery from EntityService to ensure we have persisted state
            ObjectNode persisted = (ObjectNode) entityService.getItem("Delivery", "1", java.util.UUID.fromString(delivery.getId())).join();
            if (persisted != null) {
                delivery.setAttempts(persisted.path("attempts").asInt(delivery.getAttempts() != null ? delivery.getAttempts() : 0));
                delivery.setRetriesPolicy(mapper.convertValue(persisted.path("retries_policy"), Map.class));
            }

            // increment attempts
            int attempts = delivery.getAttempts() != null ? delivery.getAttempts() : 0;
            attempts += 1;
            delivery.setAttempts(attempts);

            // resolve subscriber and catfact for sending
            Subscriber subscriber = null;
            CatFact fact = null;
            if (delivery.getSubscriberId() != null && !delivery.getSubscriberId().isEmpty()) {
                try {
                    ObjectNode subNode = (ObjectNode) entityService.getItem(Subscriber.ENTITY_NAME, String.valueOf(Subscriber.ENTITY_VERSION), java.util.UUID.fromString(delivery.getSubscriberId())).join();
                    if (subNode != null) {
                        subscriber = mapper.convertValue(subNode, Subscriber.class);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to load subscriber {} for delivery {}: {}", delivery.getSubscriberId(), delivery.getId(), e.getMessage());
                }
            }
            if (delivery.getFactId() != null && !delivery.getFactId().isEmpty()) {
                try {
                    ObjectNode factNode = (ObjectNode) entityService.getItem("CatFact", "1", java.util.UUID.fromString(delivery.getFactId())).join();
                    if (factNode != null) {
                        fact = mapper.convertValue(factNode, CatFact.class);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to load CatFact {} for delivery {}: {}", delivery.getFactId(), delivery.getId(), e.getMessage());
                }
            }

            // Implement actual send: call external email API (simulate via entityService.addItem to OutboundEvent)
            boolean success = false;
            String errorMessage = null;
            try {
                // TODO: integrate with real email provider. For now, mock success when attempts is even to simulate retries
                success = (attempts % 2) == 0;
                if (success) {
                    delivery.setStatus("SENT");
                    delivery.setSentAt(Instant.now().toString());

                    ObjectNode outbound = mapper.createObjectNode();
                    outbound.put("id", java.util.UUID.randomUUID().toString());
                    outbound.put("delivery_id", delivery.getId());
                    outbound.put("event_type", "SendSucceeded");
                    outbound.put("timestamp", Instant.now().toString());
                    ObjectNode details = mapper.createObjectNode();
                    details.put("subscriber_email", subscriber != null ? subscriber.getEmail() : "");
                    details.put("fact_id", delivery.getFactId() != null ? delivery.getFactId() : "");
                    outbound.set("details", details);
                    // Persist OutboundEvent
                    try {
                        entityService.addItem("OutboundEvent", "1", outbound).join();
                    } catch (Exception ex) {
                        logger.warn("Failed to persist OutboundEvent for delivery {}: {}", delivery.getId(), ex.getMessage());
                    }

                    logger.info("Delivery {} sent successfully on attempt {}", delivery.getId(), attempts);
                } else {
                    Map<String, Object> rp = delivery.getRetriesPolicy();
                    int maxRetries = DEFAULT_MAX_RETRIES;
                    if (rp != null && rp.get("maxRetries") instanceof Number) {
                        maxRetries = ((Number) rp.get("maxRetries")).intValue();
                    }
                    int maxAttempts = 1 + maxRetries;
                    if (attempts < maxAttempts) {
                        // schedule retry via creating another Delivery or updating scheduled_at; for now just log
                        logger.info("Delivery {} failed on attempt {} — will retry (maxAttempts={})", delivery.getId(), attempts, maxAttempts);
                        // persist attempt count
                        ObjectNode update = mapper.createObjectNode();
                        update.put("attempts", attempts);
                        try {
                            entityService.updateItem("Delivery", "1", java.util.UUID.fromString(delivery.getId()), update).join();
                        } catch (Exception ex) {
                            logger.warn("Failed to persist attempt count for Delivery {}: {}", delivery.getId(), ex.getMessage());
                        }
                    } else {
                        delivery.setStatus("FAILED");
                        ObjectNode err = mapper.createObjectNode();
                        err.put("code", "SEND_FAILED");
                        err.put("message", "Attempts exhausted");
                        // set last_error by reflection via map
                        try {
                            ObjectNode update = mapper.createObjectNode();
                            update.set("last_error", err);
                            update.put("status", "FAILED");
                            entityService.updateItem("Delivery", "1", java.util.UUID.fromString(delivery.getId()), update).join();
                        } catch (Exception ex) {
                            logger.warn("Failed to persist failure state for Delivery {}: {}", delivery.getId(), ex.getMessage());
                        }

                        ObjectNode outbound = mapper.createObjectNode();
                        outbound.put("id", java.util.UUID.randomUUID().toString());
                        outbound.put("delivery_id", delivery.getId());
                        outbound.put("event_type", "SendFailed");
                        outbound.put("timestamp", Instant.now().toString());
                        ObjectNode details = mapper.createObjectNode();
                        details.put("reason", "Attempts exhausted");
                        outbound.set("details", details);
                        try {
                            entityService.addItem("OutboundEvent", "1", outbound).join();
                        } catch (Exception ex) {
                            logger.warn("Failed to persist OutboundEvent for failed delivery {}: {}", delivery.getId(), ex.getMessage());
                        }

                        logger.info("Delivery {} marked FAILED after {} attempts", delivery.getId(), attempts);
                    }
                }
            } catch (Exception ex) {
                errorMessage = ex.getMessage();
                logger.error("Error sending Delivery {}: {}", delivery.getId(), ex.getMessage(), ex);
            }

        } catch (Exception ex) {
            logger.error("Error processing Delivery {}: {}", delivery.getId(), ex.getMessage(), ex);
        }
        return delivery;
    }
}
