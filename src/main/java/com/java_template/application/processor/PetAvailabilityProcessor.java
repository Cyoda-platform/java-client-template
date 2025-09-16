package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.category.version_1.Category;
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
 * PetAvailabilityProcessor - Initialize pet for availability and perform validation
 * 
 * Transition: make_available (none → available)
 * Purpose: Initialize pet for availability and perform validation
 */
@Component
public class PetAvailabilityProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetAvailabilityProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public PetAvailabilityProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet availability for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Pet.class)
                .validate(this::isValidEntityWithMetadata, "Invalid pet entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Pet> entityWithMetadata) {
        Pet entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Pet> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Pet> context) {

        EntityWithMetadata<Pet> entityWithMetadata = context.entityResponse();
        Pet pet = entityWithMetadata.entity();

        logger.debug("Processing pet availability: {}", pet.getPetId());

        // 1. Validate pet has all required fields (name, categoryId, price) - already done in isValid()
        
        // 2. Set default values if missing
        if (pet.getCreatedAt() == null) {
            pet.setCreatedAt(LocalDateTime.now());
        }
        if (pet.getVaccinated() == null) {
            pet.setVaccinated(false);
        }

        // 3. Validate category exists using entityService
        if (pet.getCategoryId() != null) {
            try {
                ModelSpec categoryModelSpec = new ModelSpec()
                        .withName(Category.ENTITY_NAME)
                        .withVersion(Category.ENTITY_VERSION);
                
                EntityWithMetadata<Category> category = entityService.findByBusinessId(
                        categoryModelSpec, pet.getCategoryId(), "categoryId", Category.class);
                
                if (category == null) {
                    logger.error("Category not found for pet {}: {}", pet.getPetId(), pet.getCategoryId());
                    // Return pet unchanged as per requirements
                    return entityWithMetadata;
                }
            } catch (Exception e) {
                logger.error("Error validating category for pet {}: {}", pet.getPetId(), e.getMessage());
                return entityWithMetadata;
            }
        }

        // 5. Set updatedAt = current timestamp
        pet.setUpdatedAt(LocalDateTime.now());

        logger.info("Pet {} processed successfully for availability", pet.getPetId());
        return entityWithMetadata;
    }
}
