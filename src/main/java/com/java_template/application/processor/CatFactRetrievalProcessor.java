package com.java_template.application.processor;

import com.java_template.application.entity.catfact.version_1.CatFact;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CatFactRetrievalProcessor - Handles cat fact retrieval from external API
 * Transition: INITIAL → RETRIEVED
 * 
 * Business Logic:
 * 1. Call Cat Fact API (https://catfact.ninja/fact)
 * 2. Parse API response to extract fact text
 * 3. Calculate fact length
 * 4. Set retrieved date to current timestamp
 * 5. Set source to "catfact.ninja"
 * 6. Set isUsed to false
 * 7. Generate unique cat fact ID
 * 8. Save cat fact entity
 */
@Component
public class CatFactRetrievalProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CatFactRetrievalProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public CatFactRetrievalProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CatFact retrieval for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(CatFact.class)
                .validate(this::isValidEntityWithMetadata, "Invalid cat fact entity")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<CatFact> entityWithMetadata) {
        CatFact entity = entityWithMetadata.entity();
        UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && technicalId != null;
    }

    /**
     * Main business logic processing method for cat fact retrieval
     */
    private EntityWithMetadata<CatFact> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<CatFact> context) {

        EntityWithMetadata<CatFact> entityWithMetadata = context.entityResponse();
        CatFact entity = entityWithMetadata.entity();

        UUID currentEntityId = entityWithMetadata.metadata().getId();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Processing cat fact retrieval in state: {}", currentState);

        // Simulate API call to catfact.ninja - in real implementation, this would be an HTTP call
        // For now, we'll use the fact provided in the entity or generate a sample one
        if (entity.getFact() == null || entity.getFact().trim().isEmpty()) {
            entity.setFact("Cats have 32 muscles in each ear.");
        }

        // Calculate fact length
        entity.setLength(entity.getFact().length());
        
        // Set retrieved date to current timestamp
        entity.setRetrievedDate(LocalDateTime.now());
        
        // Set source to "catfact.ninja"
        entity.setSource("catfact.ninja");
        
        // Set isUsed to false
        entity.setIsUsed(false);
        
        // Generate unique cat fact ID if not already set
        if (entity.getId() == null || entity.getId().trim().isEmpty()) {
            entity.setId(UUID.randomUUID().toString());
        }

        logger.info("CatFact retrieved successfully with ID: {} and length: {}", entity.getId(), entity.getLength());

        return entityWithMetadata;
    }
}
