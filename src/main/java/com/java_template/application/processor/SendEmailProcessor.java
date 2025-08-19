package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
            .toEntity(Object.class) // accept generic delivery representation; will convert later
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Object entity) {
        return entity != null;
    }

    private Object processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Object> context) {
        Object entityObj = context.entity();
        try {
            ObjectNode deliveryNode = mapper.convertValue(entityObj, ObjectNode.class);
            if (deliveryNode == null) {
                logger.warn("SendEmailProcessor received null or invalid delivery entity");
                return entityObj;
            }

            String deliveryId = deliveryNode.path("id").asText(null);
            int attempts = deliveryNode.path("attempts").asInt(0);
            attempts += 1;
            deliveryNode.put("attempts", attempts);

            // load subscriber if present
            JsonNode subNode = null;
            String subscriberId = deliveryNode.path("subscriber_id").asText(null);
            if (subscriberId != null && !subscriberId.isEmpty()) {
                try {
                    subNode = entityService.getItem("Subscriber", "1", java.util.UUID.fromString(subscriberId)).join();
                } catch (Exception e) {
                    logger.warn("Failed to load subscriber {} for delivery {}: {}", subscriberId, deliveryId, e.getMessage());
                }
            }

            // load catfact if present
            JsonNode factNode = null;
            String factId = deliveryNode.path("fact_id").asText(null);
            if (factId != null && !factId.isEmpty()) {
                try {
                    factNode = entityService.getItem("CatFact", "1", java.util.UUID.fromString(factId)).join();
                } catch (Exception e) {
                    logger.warn("Failed to load CatFact {} for delivery {}: {}", factId, deliveryId, e.getMessage());
                }
            }

            // perform send via external API: in prototype, simulate send success on even attempts
            boolean success = (attempts % 2) == 0;

            if (success) {
                deliveryNode.put("status", "SENT");
                deliveryNode.put("sent_at", Instant.now().toString());

                // persist outbound event
                ObjectNode outbound = mapper.createObjectNode();
                outbound.put("id", java.util.UUID.randomUUID().toString());
                outbound.put("delivery_id", deliveryId != null ? deliveryId : "");
                outbound.put("event_type", "SendSucceeded");
                outbound.put("timestamp", Instant.now().toString());
                ObjectNode details = mapper.createObjectNode();
                details.put("subscriber_email", subNode != null ? subNode.path("email").asText("") : "");
                details.put("fact_id", factId != null ? factId : "");
                outbound.set("details", details);
                try {
                    entityService.addItem("OutboundEvent", "1", outbound).join();
                } catch (Exception ex) {
                    logger.warn("Failed to persist OutboundEvent for delivery {}: {}", deliveryId, ex.getMessage(), ex);
                }

                logger.info("Delivery {} sent successfully on attempt {}", deliveryId, attempts);
            } else {
                // determine retry policy snapshot on delivery
                JsonNode rpNode = deliveryNode.path("retries_policy");
                int maxRetries = DEFAULT_MAX_RETRIES;
                if (rpNode != null && rpNode.has("maxRetries")) {
                    maxRetries = rpNode.path("maxRetries").asInt(DEFAULT_MAX_RETRIES);
                }
                int maxAttempts = 1 + maxRetries;
                if (attempts < maxAttempts) {
                    // schedule retry via workflow; for prototype just log. Do not call updateItem on this entity (it will be persisted automatically).
                    logger.info("Delivery {} failed on attempt {} — will retry later (maxAttempts={})", deliveryId, attempts, maxAttempts);
                } else {
                    deliveryNode.put("status", "FAILED");
                    ObjectNode lastErr = mapper.createObjectNode();
                    lastErr.put("code", "SEND_FAILED");
                    lastErr.put("message", "Attempts exhausted");
                    deliveryNode.set("last_error", lastErr);

                    // create outbound event for failure
                    ObjectNode outbound = mapper.createObjectNode();
                    outbound.put("id", java.util.UUID.randomUUID().toString());
                    outbound.put("delivery_id", deliveryId != null ? deliveryId : "");
                    outbound.put("event_type", "SendFailed");
                    outbound.put("timestamp", Instant.now().toString());
                    ObjectNode details = mapper.createObjectNode();
                    details.put("reason", "Attempts exhausted");
                    outbound.set("details", details);
                    try {
                        entityService.addItem("OutboundEvent", "1", outbound).join();
                    } catch (Exception ex) {
                        logger.warn("Failed to persist OutboundEvent for failed delivery {}: {}", deliveryId, ex.getMessage(), ex);
                    }

                    logger.info("Delivery {} marked FAILED after {} attempts", deliveryId, attempts);
                }
            }

            // Return updated entity node; Cyoda will persist the delivery entity automatically
            return mapper.convertValue(deliveryNode, Object.class);

        } catch (Exception ex) {
            logger.error("Error processing delivery entity: {}", ex.getMessage(), ex);
            return entityObj;
        }
    }
}
