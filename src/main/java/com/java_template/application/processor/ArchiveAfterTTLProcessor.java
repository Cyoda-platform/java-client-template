package com.java_template.application.processor;

import com.java_template.application.entity.transformedpet.version_1.TransformedPet;
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

import java.time.Instant;

@Component
public class ArchiveAfterTTLProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveAfterTTLProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ArchiveAfterTTLProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ArchiveAfterTTL for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(TransformedPet.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(TransformedPet entity) {
        return entity != null && ("PUBLISHED".equals(entity.getState()) || "VIEWED".equals(entity.getState()));
    }

    private TransformedPet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<TransformedPet> context) {
        TransformedPet entity = context.entity();
        try {
            // Simple TTL handling: mark archived when invoked
            entity.setState("ARCHIVED");
            logger.info("TransformedPet {} archived by TTL processor", entity.getId());
        } catch (Exception e) {
            logger.error("Error archiving TransformedPet {}", entity == null ? "<null>" : entity.getId(), e);
        }
        return entity;
    }
}
