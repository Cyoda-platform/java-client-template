package com.java_template.application.processor;

import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;

@Component
public class PetInitializationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PetInitializationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public PetInitializationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet initialization for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
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

        // Set default values for optional fields
        if (entity.getPhotoUrls() == null) {
            entity.setPhotoUrls(new ArrayList<>());
        }
        if (entity.getTags() == null) {
            entity.setTags(new ArrayList<>());
        }
        if (entity.getVaccinated() == null) {
            entity.setVaccinated(false);
        }
        if (entity.getNeutered() == null) {
            entity.setNeutered(false);
        }
        if (entity.getMicrochipped() == null) {
            entity.setMicrochipped(false);
        }

        // Validate birth date is not in future
        if (entity.getBirthDate() != null && entity.getBirthDate().isAfter(LocalDate.now())) {
            logger.warn("Birth date is in the future, setting to null for pet: {}", entity.getName());
            entity.setBirthDate(null);
        }

        logger.info("Pet initialized successfully: {}", entity.getName());
        return entity;
    }
}
