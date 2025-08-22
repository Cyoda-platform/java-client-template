package com.java_template.application.processor;
import com.java_template.application.entity.adoptionjob.version_1.AdoptionJob;
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

import java.time.Instant;

@Component
public class NotifyStaffProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifyStaffProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public NotifyStaffProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing AdoptionJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(AdoptionJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(AdoptionJob entity) {
        return entity != null && entity.isValid();
    }

    private AdoptionJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<AdoptionJob> context) {
        AdoptionJob entity = context.entity();

        // Business logic:
        // NotifyStaffProcessor is invoked after validation to move the job forward into review
        // and notify staff if manual review is required.
        // Decision rule implemented here:
        // - If the job is in "pending" status we evaluate if manual review is required.
        // - Manual review is required when fee > 0 or notes explicitly request manual handling.
        // - If manual review is required set status -> "review" and append a resultDetails entry.
        // - Otherwise, auto-advance to "approved" (automatic decision) and record processedAt timestamp.

        String currentStatus = entity.getStatus();
        if (currentStatus == null) {
            currentStatus = "";
        }

        // Only act when job is in the expected pre-review state
        if ("pending".equalsIgnoreCase(currentStatus) || "validation".equalsIgnoreCase(currentStatus)) {
            boolean manualReview = false;

            Double fee = entity.getFee();
            if (fee != null && fee > 0.0) {
                manualReview = true;
            }

            String notes = entity.getNotes();
            if (!manualReview && notes != null && notes.toLowerCase().contains("manual")) {
                manualReview = true;
            }

            if (manualReview) {
                entity.setStatus("review");
                String prevDetails = entity.getResultDetails();
                String append = "Staff notified for manual review at " + Instant.now().toString() + ".";
                if (prevDetails == null || prevDetails.isBlank()) {
                    entity.setResultDetails(append);
                } else {
                    entity.setResultDetails(prevDetails + " " + append);
                }
                logger.info("AdoptionJob {} requires manual review; staff notified.", entity.getId());
            } else {
                // No manual review required: auto-approve and set processedAt timestamp.
                entity.setStatus("approved");
                entity.setProcessedAt(Instant.now().toString());
                String prevDetails = entity.getResultDetails();
                String append = "Auto-approved by system at " + Instant.now().toString() + ".";
                if (prevDetails == null || prevDetails.isBlank()) {
                    entity.setResultDetails(append);
                } else {
                    entity.setResultDetails(prevDetails + " " + append);
                }
                logger.info("AdoptionJob {} auto-approved by system.", entity.getId());
            }
        } else {
            logger.debug("AdoptionJob {} in status '{}'; NotifyStaffProcessor did not change state.", entity.getId(), currentStatus);
        }

        return entity;
    }
}