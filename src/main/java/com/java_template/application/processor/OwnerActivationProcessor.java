package com.java_template.application.processor;
import com.java_template.application.entity.owner.version_1.Owner;
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

@Component
public class OwnerActivationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OwnerActivationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    @Autowired
    public OwnerActivationProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Owner for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Owner.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Owner entity) {
        return entity != null && entity.isValid();
    }

    private Owner processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Owner> context) {
        Owner entity = context.entity();
        if (entity == null) return null;

        // Normalize email if present
        try {
            if (entity.getEmail() != null) {
                String normalizedEmail = entity.getEmail().trim().toLowerCase();
                entity.setEmail(normalizedEmail);
            }
        } catch (Exception e) {
            logger.warn("Failed to normalize email for owner id={}: {}", entity.getId(), e.getMessage());
        }

        // Activation rule:
        // - Only activate when owner is verified == true
        // - Ensure email uniqueness across Owner entities before activating
        // - If role is missing/blank, set default "user" on activation
        if (Boolean.TRUE.equals(entity.getVerified())) {
            try {
                // Build a simple search condition to find owners with same email (case-insensitive handled by IEQUALS)
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.email", "IEQUALS", entity.getEmail())
                );

                ArrayNode items = entityService.getItemsByCondition(
                    Owner.ENTITY_NAME,
                    String.valueOf(Owner.ENTITY_VERSION),
                    condition,
                    true
                ).join();

                int otherCount = 0;
                if (items != null) {
                    for (JsonNode node : items) {
                        if (node == null) continue;
                        JsonNode idNode = node.get("id");
                        String otherId = idNode != null && !idNode.isNull() ? idNode.asText() : null;
                        if (otherId != null && !otherId.isBlank() && !otherId.equals(entity.getId())) {
                            otherCount++;
                        }
                    }
                }

                if (otherCount > 0) {
                    // Email used by another owner -> do not proceed with activation
                    logger.warn("Owner activation skipped for id={} because email '{}' is already used by {} other owner(s)", entity.getId(), entity.getEmail(), otherCount);
                    return entity;
                }
            } catch (Exception e) {
                // On failure to verify uniqueness, avoid activating to be safe
                logger.error("Error while checking email uniqueness for owner id={}: {}", entity.getId(), e.getMessage());
                return entity;
            }

            // At this point, verified and unique email -> activate by ensuring role is set
            try {
                if (entity.getRole() == null || entity.getRole().isBlank()) {
                    entity.setRole("user");
                }
                logger.info("Owner id={} activated with role='{}'", entity.getId(), entity.getRole());
            } catch (Exception e) {
                logger.error("Failed to set role during activation for owner id={}: {}", entity.getId(), e.getMessage());
            }
        } else {
            logger.info("Owner id={} not activated because verified=false", entity.getId());
        }

        return entity;
    }
}