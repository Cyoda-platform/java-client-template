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
public class ImportUpsertProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ImportUpsertProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ImportUpsertProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ImportUpsert for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Pet.class)
            .validate(this::isValidEntity, "Invalid pet payload")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Pet pet) {
        return pet != null && (pet.getTechnicalId() != null && !pet.getTechnicalId().isEmpty())
            || (pet.getExternalId() != null && !pet.getExternalId().isEmpty());
    }

    private Pet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Pet> context) {
        Pet pet = context.entity();
        try {
            // In a real implementation we would upsert into the datastore. For the prototype we ensure status is set to CREATED when first seen.
            if (pet.getStatus() == null) {
                pet.setStatus("CREATED");
            }
            logger.info("Upserted pet {} (simulated)", pet.getTechnicalId() == null ? pet.getExternalId() : pet.getTechnicalId());
            return pet;
        } catch (Exception e) {
            logger.error("Error during upsert processing", e);
            try {
                pet.setStatus("FAILED");
            } catch (Throwable ignore) {
            }
            return pet;
        }
    }
}
