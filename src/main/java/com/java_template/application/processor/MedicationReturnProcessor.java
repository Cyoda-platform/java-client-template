package com.java_template.application.processor;

import com.java_template.application.entity.medication.version_1.Medication;
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

@Component
public class MedicationReturnProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(MedicationReturnProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public MedicationReturnProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Medication.class)
                .validate(this::isValidEntityWithMetadata, "Invalid medication wrapper")
                .map(this::processMedicationReturn)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Medication> entityWithMetadata) {
        Medication entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Medication> processMedicationReturn(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Medication> context) {

        EntityWithMetadata<Medication> entityWithMetadata = context.entityResponse();
        Medication medication = entityWithMetadata.entity();

        logger.debug("Processing return for medication lot: {}", medication.getLotNumber());

        medication.setUpdatedAt(LocalDateTime.now());
        logger.info("Medication return processed for lot: {}", medication.getLotNumber());

        return entityWithMetadata;
    }
}
