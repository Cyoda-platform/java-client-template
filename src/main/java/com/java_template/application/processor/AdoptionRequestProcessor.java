package com.java_template.application.processor;

import com.java_template.application.entity.AdoptionRequest;
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

    public AdoptionRequestProcessor() {
        logger.info("AdoptionRequestProcessor initialized");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionRequest for request id: {}", request.getId());

        AdoptionRequest requestEntity = context.getSerializer().extractEntity(request, AdoptionRequest.class);

        if (!requestEntity.isValid()) {
            logger.error("Invalid AdoptionRequest entity state");
            return context.getSerializer().responseBuilder(request)
                .withError("Invalid AdoptionRequest entity")
                .build();
        }

        // Business logic placeholder: simulate processing
        logger.info("Processing adoption request id: {}, petId: {}, adopterId: {}", requestEntity.getId(), requestEntity.getPetId(), requestEntity.getUserId());

        // Return success response with the entity unchanged
        return context.getSerializer().responseBuilder(request)
                .withEntity(requestEntity)
                .build();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "AdoptionRequestProcessor".equals(modelSpec.operationName()) &&
                "adoptionrequest".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }
}
