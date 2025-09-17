package com.java_template.application.processor;

import com.java_template.application.entity.owner.version_1.Owner;
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
 * OwnerRegistrationProcessor - Process new owner registration
 */
@Component
public class OwnerRegistrationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(OwnerRegistrationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public OwnerRegistrationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Owner registration for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Owner.class)
                .validate(this::isValidEntityWithMetadata, "Invalid owner entity wrapper")
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
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Owner> entityWithMetadata) {
        Owner entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Process new owner registration
     */
    private EntityWithMetadata<Owner> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Owner> context) {

        EntityWithMetadata<Owner> entityWithMetadata = context.entityResponse();
        Owner owner = entityWithMetadata.entity();

        logger.debug("Registering owner: {}", owner.getOwnerId());

        // Set registration date to current time if not already set
        if (owner.getRegistrationDate() == null) {
            owner.setRegistrationDate(LocalDateTime.now());
        }

        // Initialize verification status
        if (owner.getVerificationStatus() == null || owner.getVerificationStatus().trim().isEmpty()) {
            owner.setVerificationStatus("Pending");
        }

        // Set default values for optional fields if not provided
        if (owner.getHasYard() == null) {
            owner.setHasYard(false);
        }

        if (owner.getHasOtherPets() == null) {
            owner.setHasOtherPets(false);
        }

        // Initialize pet preferences if not provided
        if (owner.getPetPreferences() == null) {
            owner.setPetPreferences(new Owner.PetPreferences());
        }

        // Send welcome email (simulated)
        logger.info("Welcome email sent to: {}", owner.getEmail());

        logger.info("Owner {} registered successfully", owner.getOwnerId());

        return entityWithMetadata;
    }
}
