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

@Component
public class PetCreateProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public PetCreateProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("PetCreateProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Pet.class)
                .withErrorHandler(this::handlePetError)
                .validate(this::isValidPet, "Invalid pet state")
                .map(this::applyInitialSetup)
                .validate(this::businessValidation, "Failed business rules")
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetCreateProcessor".equals(modelSpec.operationName()) &&
               "pet".equals(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidPet(Pet pet) {
        // Simple validation example
        return pet != null && pet.getName() != null && !pet.getName().isEmpty();
    }

    private Pet applyInitialSetup(Pet pet) {
        // Example transformation or initial setup
        pet.setStatus("CREATED");
        return pet;
    }

    private boolean businessValidation(Pet pet) {
        // Custom business logic validation example
        return pet.getStatus().equals("CREATED");
    }

    private ErrorInfo handlePetError(Throwable t, Pet pet) {
        logger.error("Error processing Pet entity", t);
        return new ErrorInfo("PetProcessingError", t.getMessage());
    }
}
