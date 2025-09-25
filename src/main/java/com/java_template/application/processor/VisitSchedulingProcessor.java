package com.java_template.application.processor;

import com.java_template.application.entity.study.version_1.Study;
import com.java_template.application.entity.visit.version_1.Visit;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Processor for visit scheduling workflow
 * Handles visit planning logic, window validation, and study protocol compliance
 */
@Component
public class VisitSchedulingProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(VisitSchedulingProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public VisitSchedulingProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Visit.class)
                .validate(this::isValidEntityWithMetadata, "Invalid visit entity wrapper")
                .map(this::processVisitScheduling)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Visit> entityWithMetadata) {
        return entityWithMetadata != null && 
               entityWithMetadata.entity() != null && 
               entityWithMetadata.entity().isValid();
    }

    private EntityWithMetadata<Visit> processVisitScheduling(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Visit> context) {

        EntityWithMetadata<Visit> entityWithMetadata = context.entityResponse();
        Visit visit = entityWithMetadata.entity();

        logger.debug("Processing visit scheduling for visit: {}", visit.getVisitId());

        // Validate visit can be scheduled
        validateVisitForScheduling(visit);
        
        // Apply study protocol visit windows if not already set
        applyStudyProtocolWindows(visit);
        
        // Validate scheduling constraints
        validateSchedulingConstraints(visit);
        
        // Update visit status and timestamps
        updateVisitForScheduling(visit);

        logger.info("Visit scheduling processed for visit {}", visit.getVisitId());
        return entityWithMetadata;
    }

    private void validateVisitForScheduling(Visit visit) {
        // Check if visit is already completed or locked
        if (visit.isCompleted()) {
            throw new IllegalStateException("Cannot reschedule completed visit: " + visit.getVisitId());
        }
        
        if (visit.isLocked()) {
            throw new IllegalStateException("Cannot reschedule locked visit: " + visit.getVisitId());
        }
        
        // Validate planned date is set
        if (visit.getPlannedDate() == null) {
            throw new IllegalStateException("Planned date must be set to schedule visit");
        }
        
        // Validate planned date is not in the past (with some tolerance)
        LocalDate today = LocalDate.now();
        if (visit.getPlannedDate().isBefore(today.minusDays(1))) {
            logger.warn("Visit {} planned date {} is in the past", visit.getVisitId(), visit.getPlannedDate());
        }
        
        logger.debug("Visit {} validation passed for scheduling", visit.getVisitId());
    }

    private void applyStudyProtocolWindows(Visit visit) {
        // If visit windows are not set, try to get them from the study protocol
        if ((visit.getWindowMinusDays() == null || visit.getWindowPlusDays() == null) && 
            visit.getStudyId() != null && visit.getVisitCode() != null) {
            
            try {
                // Get study to check visit schedule
                ModelSpec studyModelSpec = new ModelSpec()
                    .withName(Study.ENTITY_NAME)
                    .withVersion(Study.ENTITY_VERSION);
                
                EntityWithMetadata<Study> studyWithMetadata = entityService.findByBusinessId(
                    studyModelSpec, visit.getStudyId(), "studyId", Study.class);
                
                if (studyWithMetadata != null) {
                    Study study = studyWithMetadata.entity();
                    applyVisitWindowsFromStudy(visit, study);
                }
            } catch (Exception e) {
                logger.warn("Could not retrieve study {} for visit window configuration: {}", 
                           visit.getStudyId(), e.getMessage());
            }
        }
        
        // Set default windows if still not set
        if (visit.getWindowMinusDays() == null) {
            visit.setWindowMinusDays(3); // Default 3 days before
        }
        if (visit.getWindowPlusDays() == null) {
            visit.setWindowPlusDays(3); // Default 3 days after
        }
    }

    private void applyVisitWindowsFromStudy(Visit visit, Study study) {
        if (study.getVisitSchedule() != null) {
            for (Study.VisitSchedule visitSchedule : study.getVisitSchedule()) {
                if (visit.getVisitCode().equals(visitSchedule.getVisitCode())) {
                    if (visit.getWindowMinusDays() == null && visitSchedule.getWindowMinusDays() != null) {
                        visit.setWindowMinusDays(visitSchedule.getWindowMinusDays());
                    }
                    if (visit.getWindowPlusDays() == null && visitSchedule.getWindowPlusDays() != null) {
                        visit.setWindowPlusDays(visitSchedule.getWindowPlusDays());
                    }
                    if (visit.getMandatoryProcedures() == null && visitSchedule.getProcedures() != null) {
                        visit.setMandatoryProcedures(visitSchedule.getProcedures());
                    }
                    
                    logger.debug("Applied study protocol windows for visit {}: -{} to +{} days", 
                               visit.getVisitId(), visit.getWindowMinusDays(), visit.getWindowPlusDays());
                    break;
                }
            }
        }
    }

    private void validateSchedulingConstraints(Visit visit) {
        // Validate visit windows are reasonable
        if (visit.getWindowMinusDays() != null && visit.getWindowMinusDays() < 0) {
            throw new IllegalArgumentException("Window minus days cannot be negative");
        }
        
        if (visit.getWindowPlusDays() != null && visit.getWindowPlusDays() < 0) {
            throw new IllegalArgumentException("Window plus days cannot be negative");
        }
        
        // Validate window is not too large (business rule)
        int totalWindow = (visit.getWindowMinusDays() != null ? visit.getWindowMinusDays() : 0) +
                         (visit.getWindowPlusDays() != null ? visit.getWindowPlusDays() : 0);
        
        if (totalWindow > 30) {
            logger.warn("Visit {} has a very large window: {} days total", visit.getVisitId(), totalWindow);
        }
        
        // Check for conflicts with other visits for the same subject
        checkForVisitConflicts(visit);
    }

    private void checkForVisitConflicts(Visit visit) {
        // This is a placeholder for visit conflict checking
        // In a real implementation, this would query for other visits for the same subject
        // and check for scheduling conflicts within the visit windows
        
        if (visit.getSubjectId() != null) {
            try {
                // Get other visits for the same subject
                // This would require a search query to find visits by subjectId
                // For now, we'll just log that we should check for conflicts
                logger.debug("Should check for visit conflicts for subject {} around date {}", 
                           visit.getSubjectId(), visit.getPlannedDate());
                
                // TODO: Implement actual conflict detection
                // 1. Search for visits with same subjectId
                // 2. Check if any visit windows overlap
                // 3. Warn or error if conflicts are found
                
            } catch (Exception e) {
                logger.warn("Could not check for visit conflicts for subject {}: {}", 
                           visit.getSubjectId(), e.getMessage());
            }
        }
    }

    private void updateVisitForScheduling(Visit visit) {
        // Update status if it's not already set appropriately
        if (visit.getStatus() == null || "draft".equalsIgnoreCase(visit.getStatus())) {
            visit.setStatus("planned");
        }
        
        // Update timestamps
        visit.setUpdatedAt(LocalDateTime.now());
        
        logger.info("Visit {} scheduled for {} with window -{} to +{} days", 
                   visit.getVisitId(), visit.getPlannedDate(), 
                   visit.getWindowMinusDays(), visit.getWindowPlusDays());
    }
}
