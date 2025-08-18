package com.java_template.application.processor;

import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
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
public class ValidateRequestProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateRequestProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public ValidateRequestProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionRequest validation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(AdoptionRequest.class)
            .validate(this::isValidEntity, "Invalid adoption request")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(AdoptionRequest req) {
        return req != null && req.getTechnicalId() != null;
    }

    private AdoptionRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionRequest> context) {
        AdoptionRequest req = context.entity();

        String status = req.getStatus();
        if (status == null || !"SUBMITTED".equalsIgnoreCase(status)) {
            logger.info("AdoptionRequest {} not in SUBMITTED state (current={}) - skipping validation", req.getTechnicalId(), status);
            return req;
        }

        // Basic validations: required ids present
        if (req.getPetTechnicalId() == null || req.getPetTechnicalId().trim().isEmpty()) {
            req.setStatus("REJECTED");
            req.setReviewNotes("Missing petTechnicalId");
            logger.warn("AdoptionRequest {} rejected - missing petTechnicalId", req.getTechnicalId());
        } else if (req.getUserTechnicalId() == null || req.getUserTechnicalId().trim().isEmpty()) {
            req.setStatus("REJECTED");
            req.setReviewNotes("Missing userTechnicalId");
            logger.warn("AdoptionRequest {} rejected - missing userTechnicalId", req.getTechnicalId());
        } else {
            req.setStatus("UNDER_REVIEW");
            logger.info("AdoptionRequest {} moved to UNDER_REVIEW", req.getTechnicalId());
        }

        if (req.getVersion() != null) req.setVersion(req.getVersion() + 1);
        return req;
    }
}
