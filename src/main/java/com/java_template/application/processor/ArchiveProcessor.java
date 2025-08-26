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

@Component
public class ArchiveProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ArchiveProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Pet for request: {}", request.getId());

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

    private boolean isValidEntity(Pet entity) {
        return entity != null && entity.isValid();
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet pet = context.entity();

        try {
            String currentStatus = pet.getAvailability_status();
            if (currentStatus != null && currentStatus.equalsIgnoreCase("PUBLISHED")) {
                logger.info("Archiving Pet id={} currentStatus={}", pet.getId(), currentStatus);
                pet.setAvailability_status("ARCHIVED");
            } else {
                logger.debug("Pet id={} not in PUBLISHED state (current={}), no archive action taken", pet.getId(), currentStatus);
            }
        } catch (Exception e) {
            logger.error("Error while processing ArchiveProcessor for pet id={}: {}", pet != null ? pet.getId() : "null", e.getMessage(), e);
            // Do not throw; let serializer/processor handle propagation. Keep entity unchanged on error.
        }

        return pet;
    }
}