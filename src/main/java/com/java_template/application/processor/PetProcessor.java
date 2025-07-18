package com.java_template.application.processor;

import com.java_template.application.entity.Pet;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Function;

@Component
public class PetProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public PetProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer(); //always follow this pattern
        logger.info("PetProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
                .toEntity(Pet.class)
                .validate(Pet::isValid, "Invalid Pet entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetProcessor".equals(modelSpec.operationName()) &&
                "pet".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    // Business logic for processing Pet entity
    private Pet processEntityLogic(Pet pet) {
        // According to processPet() flow:
        // 1. Initial State: Pet entity saved with status (AVAILABLE, PENDING, SOLD)
        // 2. Validation: Confirm data integrity (e.g., valid category)
        // 3. Processing: Update internal indexes or caches if needed
        // 4. Completion: Confirm persistence and readiness for API queries

        // Data validation handled by isValid() method already

        // Example processing logic: normalize category to lowercase
        if (pet.getCategory() != null) {
            pet.setCategory(pet.getCategory().toLowerCase());
        }

        // Example: Normalize name capitalization (capitalize first letter)
        if (pet.getName() != null && !pet.getName().isEmpty()) {
            String name = pet.getName();
            pet.setName(name.substring(0, 1).toUpperCase() + name.substring(1));
        }

        // Potential place to update internal caches or indexes if such services were available

        return pet;
    }
}
