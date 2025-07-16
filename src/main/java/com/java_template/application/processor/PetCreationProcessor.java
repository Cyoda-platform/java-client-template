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
public class PetCreationProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public PetCreationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("PetCreationProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Pet.class)
                .withErrorHandler(this::handlePetError)
                .validate(this::isValidPet, "Invalid pet entity state")
                .map(this::applyDefaultStatus)
                .validate(this::businessValidation, "Failed business rules")
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetCreationProcessor".equals(modelSpec.operationName()) &&
               "pet".equals(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidPet(Pet pet) {
        return pet != null && pet.getName() != null && !pet.getName().isEmpty();
    }

    private Pet applyDefaultStatus(Pet pet) {
        if (pet.getStatus() == null) {
            pet.setStatus("created");
        }
        return pet;
    }

    private boolean businessValidation(Pet pet) {
        // Add business validation logic if needed
        return true;
    }

    private ErrorInfo handlePetError(Throwable throwable, Pet pet) {
        logger.error("Error processing Pet entity", throwable);
        return new ErrorInfo("PetProcessingError", throwable.getMessage());
    }
}