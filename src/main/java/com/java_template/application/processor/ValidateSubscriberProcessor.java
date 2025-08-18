package com.java_template.application.processor;

import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.JsonNode;

@Component
public class ValidateSubscriberProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateSubscriberProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private static final boolean DOUBLE_OPT_IN = true; // configurable in real deployment

    public ValidateSubscriberProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
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
        Subscriber subscriber = context.entity();
        try {
            // Normalize email
            if (subscriber.getEmail() != null) {
                String normalized = subscriber.getEmail().trim().toLowerCase();
                subscriber.setEmail(normalized);
            }

            // Basic email format check
            if (subscriber.getEmail() == null || !subscriber.getEmail().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                subscriber.setStatus("rejected");
                logger.warn("Subscriber email invalid: {}", subscriber.getEmail());
                return subscriber;
            }

            // Check for existing subscriber by normalized email to ensure idempotency
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                Condition.of("$.email", "IEQUALS", subscriber.getEmail())
            );
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                Subscriber.ENTITY_NAME,
                String.valueOf(Subscriber.ENTITY_VERSION),
                condition,
                true
            );
            ArrayNode items = itemsFuture.get();
            if (items != null && items.size() > 0) {
                // Idempotent creation: adopt existing id and status
                JsonNode existing = items.get(0);
                if (existing.has("id")) {
                    subscriber.setId(existing.get("id").asText());
                }
                if (existing.has("status")) {
                    subscriber.setStatus(existing.get("status").asText());
                }
                logger.info("Found existing subscriber for email {}, adopting id {}", subscriber.getEmail(), subscriber.getId());
                return subscriber;
            }

            // Valid email and not duplicate: set status depending on double opt-in
            if (Boolean.TRUE.equals(subscriber.getConsent_given()) && DOUBLE_OPT_IN) {
                subscriber.setStatus("awaiting_confirmation");
                logger.info("Subscriber {} awaiting confirmation (double opt-in)", subscriber.getEmail());
                // SendConfirmationProcessor will be enqueued by workflow; processor only sets state
            } else {
                // Single opt-in or no confirmation required
                subscriber.setStatus("active");
                subscriber.setSubscribed_date(OffsetDateTime.now().toString());
                logger.info("Subscriber {} activated via single opt-in", subscriber.getEmail());
            }

        } catch (Exception ex) {
            logger.error("Error while validating subscriber {}: {}", subscriber.getEmail(), ex.getMessage(), ex);
            // In case of unexpected errors, mark as rejected to avoid inconsistent state
            subscriber.setStatus("rejected");
        }

        return subscriber;
    }
}
