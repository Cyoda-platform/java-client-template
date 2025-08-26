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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;

@Component
public class ValidateSubscriberProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateSubscriberProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    @Autowired
    public ValidateSubscriberProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
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

        if (entity == null) return entity;

        // Ensure signupDate is set
        if (entity.getSignupDate() == null || entity.getSignupDate().isBlank()) {
            entity.setSignupDate(OffsetDateTime.now().toString());
        }

        String email = entity.getEmail();
        boolean emailValid = false;
        if (email != null && !email.isBlank()) {
            // simple email validation
            emailValid = email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
        }

        boolean duplicate = false;
        try {
            if (email != null && !email.isBlank()) {
                // Build condition to search subscribers with same email (case-insensitive)
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.email", "IEQUALS", email)
                );

                CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    condition,
                    true
                );

                ArrayNode items = itemsFuture.join();
                if (items != null) {
                    for (JsonNode node : items) {
                        if (node == null) continue;
                        JsonNode idNode = node.get("id");
                        String otherId = idNode != null && !idNode.isNull() ? idNode.asText() : null;
                        // If another subscriber exists with same email and different id -> duplicate
                        if (otherId != null && !otherId.isBlank()) {
                            if (entity.getId() == null || !otherId.equals(entity.getId())) {
                                duplicate = true;
                                break;
                            }
                        } else {
                            // If found an entry without id, treat as duplicate to be safe
                            duplicate = true;
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to verify duplicate subscribers for email {}: {}", email, e.getMessage());
            // If we cannot determine duplicates due to error, treat as non-duplicate and let validation proceed based on email validity.
        }

        // Business rules:
        // - If email format invalid OR duplicate exists -> FAILED
        // - Otherwise -> ACTIVE
        if (!emailValid || duplicate) {
            entity.setStatus("FAILED");
            logger.info("Subscriber validation failed for email '{}'. emailValid={}, duplicate={}", email, emailValid, duplicate);
        } else {
            entity.setStatus("ACTIVE");
            logger.info("Subscriber validated and activated for email '{}'", email);
        }

        return entity;
    }
}