package com.java_template.application.processor;

import com.java_template.application.entity.DigestRequest;
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
public class DigestRequestProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public DigestRequestProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("DigestRequestProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing DigestRequest for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
            .toEntity(DigestRequest.class)
            .validate(this::isValidEntity, "Invalid DigestRequest entity state")
            .map(this::processDigestRequest)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "DigestRequestProcessor".equals(modelSpec.operationName()) &&
               "digestRequest".equals(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    // Validation method
    private boolean isValidEntity(DigestRequest digestRequest) {
        return digestRequest.isValid();
    }

    // Business processing logic
    private DigestRequest processDigestRequest(DigestRequest digestRequest) {
        // Example logic: Transition status from CREATED to PROCESSING
        if (digestRequest.getStatus() == DigestRequest.Status.CREATED) {
            digestRequest.setStatus(DigestRequest.Status.PROCESSING);
            logger.info("DigestRequest status updated to PROCESSING");
        }
        return digestRequest;
    }
}
