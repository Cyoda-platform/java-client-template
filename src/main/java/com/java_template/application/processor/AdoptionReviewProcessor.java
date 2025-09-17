package com.java_template.application.processor;

import com.java_template.application.entity.adoption.version_1.Adoption;
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

/**
 * AdoptionReviewProcessor - Start adoption review process
 */
@Component
public class AdoptionReviewProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AdoptionReviewProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AdoptionReviewProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Adoption review for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Adoption.class)
                .validate(this::isValidEntityWithMetadata, "Invalid adoption entity wrapper")
                .map(this::processEntityWithMetadataLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the EntityWithMetadata wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Adoption> entityWithMetadata) {
        Adoption entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    /**
     * Start adoption review process
     */
    private EntityWithMetadata<Adoption> processEntityWithMetadataLogic(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Adoption> context) {

        EntityWithMetadata<Adoption> entityWithMetadata = context.entityResponse();
        Adoption adoption = entityWithMetadata.entity();
        String currentState = entityWithMetadata.metadata().getState();

        logger.debug("Starting review for adoption: {} in state: {}", adoption.getAdoptionId(), currentState);

        // Verify adoption is in applied state
        if (!"applied".equals(currentState)) {
            logger.warn("Adoption {} is not in applied state, current state: {}", adoption.getAdoptionId(), currentState);
        }

        // Assign to staff reviewer (simulated)
        logger.info("Adoption {} assigned to staff reviewer", adoption.getAdoptionId());

        // Schedule home visit if required
        if (adoption.getHomeVisitRequired() != null && adoption.getHomeVisitRequired()) {
            scheduleHomeVisit(adoption);
        }

        // Perform background checks (simulated)
        performBackgroundChecks(adoption);

        // Add staff notes about review initiation
        String reviewNotes = "Review started on " + LocalDateTime.now() + ". Background checks initiated.";
        if (adoption.getStaffNotes() != null && !adoption.getStaffNotes().trim().isEmpty()) {
            adoption.setStaffNotes(adoption.getStaffNotes() + " | " + reviewNotes);
        } else {
            adoption.setStaffNotes(reviewNotes);
        }

        logger.info("Adoption review {} started successfully", adoption.getAdoptionId());

        return entityWithMetadata;
    }

    /**
     * Schedule home visit for the adoption
     */
    private void scheduleHomeVisit(Adoption adoption) {
        // Schedule home visit for 3 days from now (simulated)
        LocalDateTime homeVisitDate = LocalDateTime.now().plusDays(3);
        adoption.setHomeVisitDate(homeVisitDate);
        
        logger.info("Home visit scheduled for adoption {} on {}", adoption.getAdoptionId(), homeVisitDate);
    }

    /**
     * Perform background checks for the adoption
     */
    private void performBackgroundChecks(Adoption adoption) {
        // Simulated background check process
        logger.info("Background checks initiated for adoption {}", adoption.getAdoptionId());
        
        // In a real implementation, this would integrate with external services
        // For now, we just log the action
    }
}
