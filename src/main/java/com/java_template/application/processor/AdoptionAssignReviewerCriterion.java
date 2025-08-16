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

import java.time.Instant;

@Component
public class AdoptionAssignReviewerCriterion implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AdoptionAssignReviewerCriterion.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AdoptionAssignReviewerCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing reviewer assignment for adoption request: {}", request.getId());

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
        return req != null && req.getTechnicalId() != null && !req.getTechnicalId().isEmpty();
    }

    private AdoptionRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionRequest> context) {
        AdoptionRequest req = context.entity();
        try {
            String status = req.getStatus();
            if (status != null && !"SUBMITTED".equals(status)) {
                logger.info("AdoptionRequest {} is in status {} - skipping reviewer assignment", req.getTechnicalId(), status);
                return req;
            }

            // Assign a reviewer automatically (simple round-robin or static assignment in prototype)
            String assignedReviewer = "auto-reviewer";
            try {
                req.setReviewer(assignedReviewer);
            } catch (Throwable ignore) {
            }

            // Move to IN_REVIEW
            req.setStatus("IN_REVIEW");
            logger.info("AdoptionRequest {} assigned reviewer {} and moved to IN_REVIEW", req.getTechnicalId(), assignedReviewer);
            return req;
        } catch (Exception e) {
            logger.error("Error while assigning reviewer for adoption request {}", req == null ? "<null>" : req.getTechnicalId(), e);
            try {
                if (req != null) {
                    req.setStatus("SUBMITTED");
                }
            } catch (Throwable ignore) {
            }
            return req;
        }
    }
}
