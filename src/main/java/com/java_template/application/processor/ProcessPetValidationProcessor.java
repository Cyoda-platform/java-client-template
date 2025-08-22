package com.java_template.application.processor;
import com.java_template.application.entity.pet.version_1.Pet;
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

@Component
public class ProcessPetValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ProcessPetValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ProcessPetValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Pet.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Pet entity) {
        if (entity == null) {
            logger.warn("Pet entity is null during validation.");
            return false;
        }

        String name = entity.getName();
        String species = entity.getSpecies();

        boolean hasName = name != null && !name.trim().isEmpty();
        boolean hasSpecies = species != null && !species.trim().isEmpty();

        if (!hasName || !hasSpecies) {
            // mark as validation failed per business requirements
            try {
                entity.setStatus("validation_failed");
            } catch (Exception e) {
                // In case setter not present or other issues, just log
                logger.warn("Unable to set status on Pet entity: {}", e.getMessage());
            }
            logger.info("Pet validation failed (missing required fields). petId={}, namePresent={}, speciesPresent={}",
                entity.getId(), hasName, hasSpecies);
            // Emit domain event: PetValidationFailed - since event emission API is not specified here,
            // we at least log the intent. Integration with domain event mechanisms should be handled by framework.
            logger.debug("Emitting event PetValidationFailed for petId={}", entity.getId());
            return false;
        }

        return true;
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet entity = context.entity();

        if (entity == null) {
            logger.warn("Received null Pet entity in processEntityLogic.");
            return null;
        }

        String currentStatus = entity.getStatus();

        // Idempotent transition: only set to 'validated' if not already in a later or same state
        if (currentStatus == null || "new".equalsIgnoreCase(currentStatus) || "validation_failed".equalsIgnoreCase(currentStatus)) {
            entity.setStatus("validated");
            logger.info("Pet validated. petId={}, previousStatus={}", entity.getId(), currentStatus);
            // Emit domain event: PetValidated (logged here; actual event emission handled elsewhere)
            logger.debug("Emitting event PetValidated for petId={}", entity.getId());
        } else {
            logger.info("Pet validation skipped (currentStatus={}). petId={}", currentStatus, entity.getId());
        }

        return entity;
    }
}