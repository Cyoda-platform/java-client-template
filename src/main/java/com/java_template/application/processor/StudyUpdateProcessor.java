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

import java.time.LocalDateTime;

/**
 * Processor for updating study information
 * 
 * Handles updates to study metadata, timeline, and other non-state-changing modifications.
 */
@Component
public class StudyUpdateProcessor implements CyodaProcessor {
    private static final Logger logger = LoggerFactory.getLogger(StudyUpdateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public StudyUpdateProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Study.class)
                .validate(this::isValidEntityWithMetadata, "Invalid study entity wrapper")
                .map(this::processStudyUpdate)
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

        logger.info("Successfully validated study: {}", study.getStudyId());
        return true;
    }

    /**
     * Processes the study update
     */
    private EntityWithMetadata<Study> processStudyUpdate(EntityWithMetadata<Study> entityWithMetadata) {
        Study study = entityWithMetadata.entity();
        logger.info("Processing study update for study: {}", study.getStudyId());

        try {
            // Update audit fields
            study.setUpdatedAt(LocalDateTime.now());
            
            // Validate study timeline consistency
            validateStudyTimeline(study);
            
            // Validate enrollment numbers
            validateEnrollmentNumbers(study);
            
            logger.info("Successfully processed study update for study: {}", study.getStudyId());
            return entityWithMetadata;
            
        } catch (Exception e) {
            logger.error("Error processing study update for study: {}", study.getStudyId(), e);
            throw new RuntimeException("Failed to process study update: " + e.getMessage(), e);
        }
    }

    /**
     * Validates study timeline consistency
     */
    private void validateStudyTimeline(Study study) {
        if (study.getPlannedStartDate() != null && study.getPlannedEndDate() != null) {
            if (study.getPlannedStartDate().isAfter(study.getPlannedEndDate())) {
                throw new IllegalArgumentException("Planned start date cannot be after planned end date");
            }
        }

        if (study.getActualStartDate() != null && study.getActualEndDate() != null) {
            if (study.getActualStartDate().isAfter(study.getActualEndDate())) {
                throw new IllegalArgumentException("Actual start date cannot be after actual end date");
            }
        }

        if (study.getFirstPatientIn() != null && study.getLastPatientOut() != null) {
            if (study.getFirstPatientIn().isAfter(study.getLastPatientOut())) {
                throw new IllegalArgumentException("First patient in date cannot be after last patient out date");
            }
        }
    }

    /**
     * Validates enrollment numbers
     */
    private void validateEnrollmentNumbers(Study study) {
        if (study.getCurrentEnrollment() != null && study.getCurrentEnrollment() < 0) {
            throw new IllegalArgumentException("Current enrollment cannot be negative");
        }

        if (study.getActualEnrollment() != null && study.getActualEnrollment() < 0) {
            throw new IllegalArgumentException("Actual enrollment cannot be negative");
        }

        if (study.getPlannedEnrollment() != null && study.getPlannedEnrollment() < 0) {
            throw new IllegalArgumentException("Planned enrollment cannot be negative");
        }

        if (study.getCurrentEnrollment() != null && study.getActualEnrollment() != null) {
            if (study.getCurrentEnrollment() > study.getActualEnrollment()) {
                throw new IllegalArgumentException("Current enrollment cannot exceed actual enrollment");
            }
        }
    }
}
