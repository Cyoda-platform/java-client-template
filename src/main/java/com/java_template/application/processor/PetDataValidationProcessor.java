package com.java_template.application.processor;

import com.java_template.application.entity.pet_entity.version_1.PetEntity;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * PetDataValidationProcessor - Validate and enrich pet data with additional business logic
 * Transition: validate_pet_data (extracted → validated)
 */
@Component
public class PetDataValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetDataValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PetDataValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetDataValidation for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(PetEntity.class)
                .validate(this::isValidEntityWithMetadata, "Invalid entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<PetEntity> entityWithMetadata) {
        PetEntity entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && technicalId != null;
    }

    private EntityWithMetadata<PetEntity> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<PetEntity> context) {

        EntityWithMetadata<PetEntity> entityWithMetadata = context.entityResponse();
        PetEntity entity = entityWithMetadata.entity();

        logger.debug("Validating pet data for entity: {}", entity.getPetId());

        // Validate current entity data
        validateEntityData(entity);

        // Enrich entity data
        enrichEntityData(entity);

        logger.info("Pet data validation completed for entity: {}", entity.getPetId());

        return entityWithMetadata;
    }

    /**
     * Validate essential pet data fields
     */
    private void validateEntityData(PetEntity entity) {
        if (entity.getPetId() == null || entity.getName() == null || entity.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Pet ID and name are required fields");
        }

        if (entity.getCategory() == null) {
            throw new IllegalArgumentException("Pet category is required");
        }

        logger.debug("Pet data validation passed for entity: {}", entity.getPetId());
    }

    /**
     * Enrich entity data with additional business logic
     */
    private void enrichEntityData(PetEntity entity) {
        // Set default price if null
        if (entity.getPrice() == null) {
            entity.setPrice(calculateDefaultPrice(entity.getCategory().getName()));
        }

        // Ensure stock level is non-negative
        if (entity.getStockLevel() != null && entity.getStockLevel() < 0) {
            entity.setStockLevel(0);
        }

        // Initialize sales metrics if null
        if (entity.getSalesVolume() == null) {
            entity.setSalesVolume(0);
        }

        if (entity.getRevenue() == null) {
            entity.setRevenue(0.0);
        }

        // Update timestamp
        entity.setUpdatedAt(LocalDateTime.now());

        logger.debug("Pet data enrichment completed for entity: {}", entity.getPetId());
    }

    /**
     * Calculate default price based on category
     */
    private Double calculateDefaultPrice(String categoryName) {
        if (categoryName == null) {
            return 100.0;
        }

        switch (categoryName.toLowerCase()) {
            case "dogs":
                return 300.0;
            case "cats":
                return 200.0;
            case "birds":
                return 150.0;
            case "fish":
                return 50.0;
            case "reptiles":
                return 250.0;
            default:
                return 100.0;
        }
    }
}
