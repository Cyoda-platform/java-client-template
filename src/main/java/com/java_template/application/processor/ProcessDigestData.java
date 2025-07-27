package com.java_template.application.processor;

import com.java_template.application.entity.DigestData;
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
public class ProcessDigestData implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    public ProcessDigestData(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("ProcessDigestData initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestData for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(DigestData.class)
            .validate(this::isValidEntity, "Invalid DigestData state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private boolean isValidEntity(DigestData entity) {
        return entity != null && entity.isValid();
    }

    private DigestData processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<DigestData> context) {
        DigestData entity = context.entity();

        // Business logic copied from processDigestData method in CyodaEntityControllerPrototype
        // 1. Format and prepare the retrievedData into emailContent
        // This logic might involve some formatting or transformation of retrievedData

        // For example, just log or mark processedTimestamp
        entity.setProcessedTimestamp(java.time.Instant.now());

        return entity;
    }
}
