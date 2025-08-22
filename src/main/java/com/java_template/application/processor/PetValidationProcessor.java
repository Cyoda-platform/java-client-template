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
import java.time.Instant;

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
        return entity != null && entity.isValid();
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet entity = context.entity();

        // Idempotency: if already validated or failed, skip re-processing
        String currentStatus = entity.getStatus();
        if (currentStatus != null) {
            String normalized = currentStatus.trim().toLowerCase();
            if ("validated".equals(normalized) || "validation_failed".equals(normalized) || "archived".equals(normalized) || "adopted".equals(normalized)) {
                logger.info("Pet [{}] already in terminal/processed status '{}', skipping validation.", entity.getId(), currentStatus);
                return entity;
            }
        }

        // Basic required-field validation per functional requirements:
        // - name required and non-blank
        // - species required and non-blank
        String name = entity.getName();
        String species = entity.getSpecies();

        boolean nameMissing = (name == null || name.trim().isEmpty());
        boolean speciesMissing = (species == null || species.trim().isEmpty());

        if (nameMissing || speciesMissing) {
            logger.warn("Pet [{}] validation failed. Missing fields: nameMissing={}, speciesMissing={}", entity.getId(), nameMissing, speciesMissing);
            entity.setStatus("validation_failed");
            // update timestamp if available
            try {
                entity.setUpdatedAt(Instant.now().toString());
            } catch (Exception e) {
                logger.debug("Unable to set updatedAt on Pet [{}]: {}", entity.getId(), e.getMessage());
            }
            return entity;
        }

        // All basic checks passed -> mark as validated
        logger.info("Pet [{}] passed basic validation. Marking as 'validated'.", entity.getId());
        entity.setStatus("validated");
        try {
            entity.setUpdatedAt(Instant.now().toString());
        } catch (Exception e) {
            logger.debug("Unable to set updatedAt on Pet [{}]: {}", entity.getId(), e.getMessage());
        }

        return entity;
    }
}