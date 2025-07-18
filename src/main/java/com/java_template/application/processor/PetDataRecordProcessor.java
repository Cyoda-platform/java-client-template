package com.java_template.application.processor;

import com.java_template.application.entity.PetDataRecord;
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
public class PetDataRecordProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public PetDataRecordProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer(); //always follow this pattern
        logger.info("PetDataRecordProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing PetDataRecord for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
                .toEntity(PetDataRecord.class)
                .validate(this::isValidEntity, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "PetDataRecordProcessor".equals(modelSpec.operationName()) &&
                "petDataRecord".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(PetDataRecord entity) {
        return entity.isValid();
    }

    private PetDataRecord processEntityLogic(PetDataRecord entity) {
        // Business logic from processPetDataRecord()
        // Initial state: PetDataRecord with NEW status
        // Transformation: Prepare data for email content
        // Update PetDataRecord status to PROCESSED

        // Check if current status is NEW before processing
        if ("NEW".equalsIgnoreCase(entity.getStatus())) {
            // Here you can add data transformation logic if needed
            // For now, we just update the status to PROCESSED
            entity.setStatus("PROCESSED");
        }

        return entity;
    }
}
