package com.java_template.application.processor;

import com.java_template.application.entity.Pet;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PetJobAddProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public PetJobAddProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("PetJobAddProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet entity for Add operation, request id: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Pet.class)
                .validate(pet -> pet.getName() != null && !pet.getName().isBlank()
                        && pet.getCategory() != null && !pet.getCategory().isBlank(), "Pet must have non-blank name and category")
                .map(this::processPet)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetJobAddProcessor".equals(modelSpec.operationName()) &&
                "pet".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private Pet processPet(Pet pet) {
        logger.info("Processing Pet with technicalId: {}", pet.getTechnicalId());
        if (pet.getName() == null || pet.getName().isBlank() || pet.getCategory() == null || pet.getCategory().isBlank()) {
            logger.error("Pet validation failed for technicalId: {}", pet.getTechnicalId());
            throw new IllegalArgumentException("Pet must have non-blank name and category");
        }
        // Future scope: indexing, notifications, other processing workflows
        logger.info("Pet processing completed for technicalId: {}", pet.getTechnicalId());
        return pet;
    }
}
