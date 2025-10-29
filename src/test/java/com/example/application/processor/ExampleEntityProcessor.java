package com.example.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.application.entity.example_entity.version_1.ExampleEntity;
import com.example.application.entity.example_entity.version_1.OtherEntity;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.common.condition.GroupCondition;
import org.cyoda.cloud.api.event.common.condition.Operation;
import org.cyoda.cloud.api.event.common.condition.SimpleCondition;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Golden Example Processor - Template for creating new processors
 * <p>
 * This is a generified example processor that demonstrates:
 * - Proper CyodaProcessor implementation
 * - EntityWithMetadata processing pattern
 * - Validation and business logic
 * - Interaction with other entities (when needed)
 * - Error handling and logging
 * - Performance considerations
 * <p>
 * To create a new processor:
 * 1. Copy this file to your processor package
 * 2. Rename class from ExampleEntityProcessor to YourProcessorName
 * 3. Update entity type from ExampleEntity to your entity
 * 4. Implement your specific business logic in processEntityWithMetadataLogic()
 * 5. Update validation logic in isValidEntityWithMetadata()
 * 6. Add EntityService injection only if you need to interact with OTHER entities
 * 7. Update supports() method to match your processor name
 */
@Component
public class ExampleEntityProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ExampleEntityProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    // Optional: Only inject EntityService if you need to interact with OTHER entities
    private final EntityService entityService;

    public ExampleEntityProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }


    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ExampleEntity for request: {}", request.getId());

        // ✅ CORRECT: Unified EntityWithMetadata processing (RECOMMENDED)
        return serializer.withRequest(request)
                .toEntityWithMetadata(ExampleEntity.class)  // Unified interface with controllers
                .validate(this::isValidEntityWithMetadata, "Invalid entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     * This method checks both the entity and metadata are valid
     */
    private boolean isValidEntityWithMetadata(com.java_template.common.dto.EntityWithMetadata<ExampleEntity> entityWithMetadata) {
        ExampleEntity entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid(entityWithMetadata.metadata()) && technicalId != null;
    }

    /**
     * Main business logic processing method
     * This is where you implement your specific business logic
     * <p>
     * CRITICAL LIMITATIONS:
     * - ✅ ALLOWED: Read current entity data
     * - ✅ ALLOWED: Update OTHER entities via EntityService
     * - ❌ FORBIDDEN: Update current entity state/transitions
     * - ❌ FORBIDDEN: Use ObjectMapper or manual JSON manipulation
     */
    private EntityWithMetadata<ExampleEntity> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<ExampleEntity> context) {

        EntityWithMetadata<ExampleEntity> entityWithMetadata = context.entityResponse();
        ExampleEntity entity = entityWithMetadata.entity();

        // Get current entity metadata (CRITICAL: Cannot update current entity)
        java.util.UUID currentEntityId = entityWithMetadata.metadata().getId(); // Technical ID of current entity (UUID)
        String currentState = entityWithMetadata.metadata().getState(); // Current state from metadata - only if necessary

        logger.debug("Processing entity: {} in state: {}", entity.getExampleId(), currentState);

        // Example business logic: Calculate totals and validate items
        processItems(entity);

        // Example: Update timestamps
        entity.setUpdatedAt(LocalDateTime.now());

        // Example: Interact with OTHER entities (only if EntityService is injected)
        if (entityService != null) {
            processRelatedEntities(entity);
        }

        logger.info("ExampleEntity {} processed successfully", entity.getExampleId());

        // CRITICAL: Return EntityWithMetadata (cannot change current entity state)
        return entityWithMetadata;
    }

    /**
     * Process items in the entity - example business logic
     */
    private void processItems(ExampleEntity entity) {
        if (entity.getItems() != null && !entity.getItems().isEmpty()) {
            // Calculate totals for each item
            for (ExampleEntity.ExampleItem item : entity.getItems()) {
                if (item.getPrice() != null && item.getQty() != null) {
                    Double itemTotal = item.getPrice() * item.getQty();
                    item.setItemTotal(itemTotal);
                }
            }

            // Update entity total quantity and amount
            int totalQuantity = entity.getItems().stream()
                    .mapToInt(item -> item.getQty() != null ? item.getQty() : 0)
                    .sum();
            entity.setQuantity(totalQuantity);

            Double totalAmount = entity.getItems().stream()
                    .mapToDouble(item -> item.getItemTotal() != null ? item.getItemTotal() : 0.0)
                    .sum();
            entity.setAmount(totalAmount);
        }
    }

    /**
     * Process related entities - example of interacting with OTHER entities
     * Only called if EntityService is injected
     */
    private void processRelatedEntities(ExampleEntity entity) {
        // Example: Find related entities and update them
        // This is where you would interact with OTHER entities, not the current one
        logger.debug("Processing related entities for: {}", entity.getExampleId()); //this is business id

        // Example: Update related other entities using streaming API for memory efficiency
        ModelSpec modelSpec = new ModelSpec().withName(OtherEntity.ENTITY_NAME).withVersion(OtherEntity.ENTITY_VERSION);
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleCondition simpleCondition = new SimpleCondition()
                .withJsonPath("$.someField")
                .withOperation(Operation.EQUALS)
                .withValue(objectMapper.valueToTree("someValue"));

        GroupCondition condition = new GroupCondition()
                .withOperator(GroupCondition.Operator.AND)
                .withConditions(List.of(simpleCondition));

        // Use streaming API for memory-efficient processing of related entities
        try (var stream = entityService.searchAsStream(modelSpec, condition, OtherEntity.class, 100, true, null)) {
            stream.forEach(otherEntityWithMetadata -> {
                OtherEntity otherEntity = otherEntityWithMetadata.entity();
                otherEntity.setName("new_name");
                entityService.update(otherEntityWithMetadata.metadata().getId(), otherEntity, "UPDATE");
            });
        }
    }
}
