package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
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

import java.util.ArrayList;
import java.util.List;

@Component
public class PetValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PetValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet validation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Pet pet) {
        // basic non-null checks - ensure request contains a pet with an identifier
        return pet != null && pet.getPetId() != null && !pet.getPetId().isEmpty();
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet pet = context.entity();
        try {
            String status = pet.getStatus();
            if (status != null && !("CREATED".equals(status) || "VALIDATING".equals(status))) {
                logger.info("Pet {} is in status {} - skipping validation (idempotent guard)", pet.getPetId(), status);
                return pet;
            }

            // mark as validating
            pet.setStatus("VALIDATING");

            // Validate required fields
            List<String> errors = new ArrayList<>();
            if (pet.getName() == null || pet.getName().trim().isEmpty()) {
                errors.add("name is required");
            }
            if (pet.getSpecies() == null || pet.getSpecies().trim().isEmpty()) {
                errors.add("species is required");
            }
            Integer age = pet.getAge();
            if (age == null || age < 0) {
                errors.add("age must be provided and non-negative");
            }

            if (!errors.isEmpty()) {
                pet.setStatus("FAILED");
                // If entity exposes a place to record validation notes, prefer that. Use description if available.
                try {
                    pet.setDescription(String.join("; ", errors));
                } catch (Throwable t) {
                    // ignore if setter doesn't exist
                }
                logger.warn("Pet {} failed validation: {}", pet.getPetId(), errors);
                return pet;
            }

            // Passed validation
            pet.setStatus("VALIDATED");
            logger.info("Pet {} validated successfully", pet.getPetId());
            return pet;
        } catch (Exception e) {
            logger.error("Unhandled error while validating pet {}", pet == null ? "<null>" : pet.getPetId(), e);
            if (pet != null) {
                pet.setStatus("FAILED");
                try {
                    pet.setDescription("Validation processor error: " + e.getMessage());
                } catch (Throwable ignore) {
                }
            }
            return pet;
        }
    }
}
