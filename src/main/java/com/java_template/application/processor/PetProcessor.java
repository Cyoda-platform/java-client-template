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

import java.util.function.BiFunction;
import java.util.function.Function;

@Component
public class PetProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public PetProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("PetProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Pet.class)
                .withErrorHandler(this::handlePetError)
                .validate(this::isValidPet, "Invalid pet state")
                .map(this::applyBusinessLogic)
                .validate(this::businessValidation, "Failed business rules")
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetProcessor".equals(modelSpec.operationName()) &&
                "pet".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidPet(Pet pet) {
        return pet.getId() != null && pet.getName() != null && !pet.getName().isEmpty();
    }

    private ErrorInfo handlePetError(Throwable throwable, Pet pet) {
        logger.error("Error processing Pet: {}", throwable.getMessage(), throwable);
        return new ErrorInfo("PetProcessingError", throwable.getMessage());
    }

    private Pet applyBusinessLogic(Pet pet) {
        // Example business logic: if pet status is null, set it to 'available'
        if (pet.getStatus() == null || pet.getStatus().isEmpty()) {
            pet.setStatus("available");
        }
        return pet;
    }

    private boolean businessValidation(Pet pet) {
        // Example business validation: status must be one of allowed values
        return pet.getStatus() != null && (pet.getStatus().equals("available") || pet.getStatus().equals("pending") || pet.getStatus().equals("sold"));
    }

}
