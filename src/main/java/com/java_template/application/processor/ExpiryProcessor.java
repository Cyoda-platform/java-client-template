package com.java_template.application.processor;

import com.java_template.application.entity.flightOption.version_1.FlightOption;
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

import java.time.OffsetDateTime;

@Component
public class ExpiryProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ExpiryProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ExpiryProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ExpiryProcessor for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(FlightOption.class)
            .validate(this::isValidEntity, "Invalid entity state for expiry")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(FlightOption entity) {
        return entity != null && entity.getStatus() != null && ("UNAVAILABLE".equalsIgnoreCase(entity.getStatus()) || "READY".equalsIgnoreCase(entity.getStatus()));
    }

    private FlightOption processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<FlightOption> context) {
        FlightOption entity = context.entity();
        try {
            // For prototype: mark as ARCHIVED if older than some TTL. Since we don't have createdAt parsing, archive immediately for demo
            logger.debug("Archiving option {}", entity.getTechnicalId());
            entity.setStatus("ARCHIVED");
            entity.setUpdatedAt(OffsetDateTime.now().toString());
            return entity;
        } catch (Exception ex) {
            logger.error("Error archiving option {}", entity.getTechnicalId(), ex);
            entity.setStatus("ERROR");
            entity.setErrorMessage("Expiry processing failed: " + ex.getMessage());
            entity.setUpdatedAt(OffsetDateTime.now().toString());
            return entity;
        }
    }
}
