package com.java_template.application.processor;

import com.java_template.application.entity.study.version_1.Study;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Processor for activating a study
 * 
 * Handles the activation of an approved study, setting actual start date
 * and performing necessary validations.
 */
@Component
public class StudyActivationProcessor implements CyodaProcessor {
    private static final Logger logger = LoggerFactory.getLogger(StudyActivationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public StudyActivationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Study.class)
                .validate(this::isValidEntityWithMetadata, "Invalid study entity wrapper")
                .map(this::processStudyActivation)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    /**
     * Validates the entity wrapper
     */
    private boolean isValidEntityWithMetadata(EntityWithMetadata<Study> entityWithMetadata) {
        if (entityWithMetadata == null || entityWithMetadata.entity() == null) {
            logger.error("Entity or metadata is null");
            return false;
        }

        Study study = entityWithMetadata.entity();
        if (!study.isValid()) {
            logger.error("Study entity validation failed for study: {}", study.getStudyId());
            return false;
        }

        // Validate that study is in approved state (this would be checked via metadata.getState())
        String currentState = entityWithMetadata.metadata().getState();
        if (!"approved".equals(currentState)) {
            logger.error("Study {} is not in approved state. Current state: {}", study.getStudyId(), currentState);
            return false;
        }

        logger.info("Successfully validated study for activation: {}", study.getStudyId());
        return true;
    }

    /**
     * Processes the study activation
     */
    private EntityWithMetadata<Study> processStudyActivation(EntityWithMetadata<Study> entityWithMetadata) {
        Study study = entityWithMetadata.entity();
        logger.info("Processing study activation for study: {}", study.getStudyId());

        try {
            // Set actual start date if not already set
            if (study.getActualStartDate() == null) {
                study.setActualStartDate(LocalDate.now());
                logger.info("Set actual start date for study: {}", study.getStudyId());
            }

            // Initialize current enrollment to 0 if not set
            if (study.getCurrentEnrollment() == null) {
                study.setCurrentEnrollment(0);
                logger.info("Initialized current enrollment to 0 for study: {}", study.getStudyId());
            }

            // Validate activation prerequisites
            validateActivationPrerequisites(study);

            // Update audit fields
            study.setUpdatedAt(LocalDateTime.now());
            
            logger.info("Successfully activated study: {}", study.getStudyId());
            return entityWithMetadata;
            
        } catch (Exception e) {
            logger.error("Error activating study: {}", study.getStudyId(), e);
            throw new RuntimeException("Failed to activate study: " + e.getMessage(), e);
        }
    }

    /**
     * Validates prerequisites for study activation
     */
    private void validateActivationPrerequisites(Study study) {
        // Check that required study information is complete
        if (study.getTitle() == null || study.getTitle().trim().isEmpty()) {
            throw new IllegalStateException("Study title is required for activation");
        }

        if (study.getPhase() == null || study.getPhase().trim().isEmpty()) {
            throw new IllegalStateException("Study phase is required for activation");
        }

        if (study.getProtocolNumber() == null || study.getProtocolNumber().trim().isEmpty()) {
            throw new IllegalStateException("Protocol number is required for activation");
        }

        if (study.getSponsor() == null) {
            throw new IllegalStateException("Sponsor information is required for activation");
        }

        if (study.getPlannedEnrollment() == null || study.getPlannedEnrollment() <= 0) {
            throw new IllegalStateException("Valid planned enrollment is required for activation");
        }

        // Check that planned start date is not in the past (with some tolerance)
        if (study.getPlannedStartDate() != null && study.getPlannedStartDate().isBefore(LocalDate.now().minusDays(30))) {
            logger.warn("Planned start date is significantly in the past for study: {}", study.getStudyId());
        }

        logger.info("All activation prerequisites validated for study: {}", study.getStudyId());
    }
}
