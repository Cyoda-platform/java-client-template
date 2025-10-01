package com.java_template.application.processor;

import com.java_template.application.entity.rfi.version_1.RFI;
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
public class RFIAnswerProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RFIAnswerProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public RFIAnswerProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(RFI.class)
                .validate(this::isValidEntityWithMetadata, "Invalid RFI wrapper")
                .map(this::processRFIAnswer)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<RFI> entityWithMetadata) {
        RFI entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<RFI> processRFIAnswer(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<RFI> context) {

        EntityWithMetadata<RFI> entityWithMetadata = context.entityResponse();
        RFI rfi = entityWithMetadata.entity();

        logger.debug("Processing answer for RFI: {}", rfi.getRfiId());

        rfi.setUpdatedAt(LocalDateTime.now());
        logger.info("RFI {} answer processed", rfi.getRfiId());

        return entityWithMetadata;
    }
}
