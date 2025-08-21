package com.java_template.application.processor;

import com.java_template.application.entity.rawpet.version_1.RawPet;
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
        logger.info("Processing ArchiveProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(RawPet.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(RawPet entity) {
        return entity != null && ("TRANSFORMED".equals(entity.getState()) || "ARCHIVED".equals(entity.getState()));
    }

    private RawPet processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<RawPet> context) {
        RawPet entity = context.entity();
        try {
            // archive by setting state
            entity.setState("ARCHIVED");
            entity.setTransformedAt(Instant.now().toString());
            logger.info("RawPet {} archived", entity.getRawId());
        } catch (Exception e) {
            logger.error("Error archiving RawPet {}", entity == null ? "<null>" : entity.getRawId(), e);
        }
        return entity;
    }
}
