package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
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
public class PetCreatedProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public PetCreatedProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("PetCreatedProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Pet.class)
                .withErrorHandler(this::handlePetError)
                .validate(this::isValidPet, "Invalid pet state")
                .map(this::applyEnrichment)
                .validate(this::businessValidation, "Failed business rules")
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetCreatedProcessor".equals(modelSpec.operationName()) &&
                "pet".equals(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidPet(Pet pet) {
        return pet != null && pet.getName() != null && !pet.getName().isBlank();
    }

    private Pet applyEnrichment(Pet pet) {
        if (pet.getDescription() == null || pet.getDescription().isBlank()) {
            pet.setDescription("New pet created");
        }
        return pet;
    }

    private boolean businessValidation(Pet pet) {
        return pet.getStatus() != null && (pet.getStatus().equals("available") || pet.getStatus().equals("pending") || pet.getStatus().equals("sold"));
    }

    private ErrorInfo handlePetError(Throwable throwable, Pet pet) {
        logger.error("Error processing pet: {}", pet, throwable);
        return new ErrorInfo("PetProcessingError", throwable.getMessage());
    }
}
