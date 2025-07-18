package com.java_template.application.processor;

import com.java_template.application.entity.AdoptionRequest;
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
public class AdoptionRequestProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public AdoptionRequestProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("AdoptionRequestProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionRequest for request: {}", request.getId());

        // Fluent entity processing with validation
        return serializer.withRequest(request)
                .toEntity(AdoptionRequest.class)
                .validate(this::isValidEntity, "Invalid AdoptionRequest state")
                .map(this::processAdoptionRequestLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "AdoptionRequestProcessor".equals(modelSpec.operationName()) &&
                "adoptionRequest".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private boolean isValidEntity(AdoptionRequest request) {
        return request != null && request.getId() != null && !request.getId().isBlank() &&
                request.getTechnicalId() != null && request.getPetId() != null && !request.getPetId().isBlank() &&
                request.getUserId() != null && !request.getUserId().isBlank() && request.getStatus() != null && !request.getStatus().isBlank();
    }

    // Copied logic from CyodaEntityControllerPrototype.processAdoptionRequest (currently only logging, no complex logic)
    private AdoptionRequest processAdoptionRequestLogic(AdoptionRequest request) {
        logger.info("Processing AdoptionRequest entity event for id {}", request.getId());
        // No additional processing logic found in prototype
        return request;
    }
}
