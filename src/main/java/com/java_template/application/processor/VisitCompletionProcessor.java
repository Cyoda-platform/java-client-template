package com.java_template.application.processor;

import com.java_template.application.entity.visit.version_1.Visit;
import com.java_template.common.dto.EntityWithMetadata;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Processor for visit completion workflow
 * Handles visit completion logic, validation, and deviation tracking
 */
@Component
public class VisitCompletionProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(VisitCompletionProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public VisitCompletionProcessor(SerializerFactory serializerFactory, EntityService entityService) {
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
                .map(this::processVisitCompletion)
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

    private EntityWithMetadata<Visit> processVisitCompletion(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Visit> context) {

        EntityWithMetadata<Visit> entityWithMetadata = context.entityResponse();
        Visit visit = entityWithMetadata.entity();

        logger.debug("Processing visit completion for visit: {}", visit.getVisitId());

        // Validate visit can be completed
        validateVisitForCompletion(visit);
        
        // Process visit completion
        completeVisit(visit);
        
        // Check for deviations
        detectAndRecordDeviations(visit);
        
        // Update timestamps
        visit.setUpdatedAt(LocalDateTime.now());
        if (visit.getCompletedAt() == null) {
            visit.setCompletedAt(LocalDateTime.now());
        }

        logger.info("Visit completion processed for visit {}", visit.getVisitId());
        return entityWithMetadata;
    }

    private void validateVisitForCompletion(Visit visit) {
        // Use the enhanced validation method from the entity
        try {
            visit.validateForOperation("complete");
        } catch (Exception e) {
            logger.error("Visit {} validation failed for completion: {}", visit.getVisitId(), e.getMessage());
            throw e;
        }

        // Check if visit is already completed
        if ("completed".equalsIgnoreCase(visit.getStatus())) {
            logger.warn("Visit {} is already completed", visit.getVisitId());
            return;
        }

        logger.debug("Visit {} validation passed for completion", visit.getVisitId());
    }

    private void completeVisit(Visit visit) {
        // Set completion status
        visit.setStatus("completed");
        
        // Set actual date if not already set
        if (visit.getActualDate() == null) {
            visit.setActualDate(LocalDate.now());
        }
        
        // Set completion timestamp if not already set
        if (visit.getCompletedAt() == null) {
            visit.setCompletedAt(LocalDateTime.now());
        }
        
        logger.info("Visit {} completed on {}", visit.getVisitId(), visit.getActualDate());
    }

    private void detectAndRecordDeviations(Visit visit) {
        List<Visit.Deviation> deviations = visit.getDeviations();
        if (deviations == null) {
            deviations = new ArrayList<>();
            visit.setDeviations(deviations);
        }
        
        // Check for timing deviations
        checkTimingDeviations(visit, deviations);
        
        // Check for procedure deviations (if mandatory procedures are defined)
        checkProcedureDeviations(visit, deviations);
        
        logger.debug("Deviation check completed for visit {}, found {} deviations", 
                    visit.getVisitId(), deviations.size());
    }

    private void checkTimingDeviations(Visit visit, List<Visit.Deviation> deviations) {
        if (visit.getPlannedDate() == null || visit.getActualDate() == null) {
            return;
        }
        
        // Check if visit is outside the allowed window
        if (!visit.isWithinWindow(visit.getActualDate())) {
            Visit.Deviation timingDeviation = new Visit.Deviation();
            timingDeviation.setDeviationId("TIMING_" + System.currentTimeMillis());
            timingDeviation.setCode("OUT_OF_WINDOW");
            timingDeviation.setCategory("timing");
            timingDeviation.setDetectedAt(LocalDateTime.now());
            
            long daysDifference = Math.abs(visit.getPlannedDate().toEpochDay() - visit.getActualDate().toEpochDay());
            
            if (daysDifference <= 3) {
                timingDeviation.setSeverity("minor");
                timingDeviation.setDescription(String.format("Visit occurred %d day(s) outside planned window", daysDifference));
            } else if (daysDifference <= 7) {
                timingDeviation.setSeverity("major");
                timingDeviation.setDescription(String.format("Visit occurred %d day(s) outside planned window", daysDifference));
            } else {
                timingDeviation.setSeverity("critical");
                timingDeviation.setDescription(String.format("Visit occurred %d day(s) outside planned window", daysDifference));
                timingDeviation.setRequiresReporting(true);
            }
            
            deviations.add(timingDeviation);
            logger.warn("Timing deviation detected for visit {}: {}", visit.getVisitId(), timingDeviation.getDescription());
        }
    }

    private void checkProcedureDeviations(Visit visit, List<Visit.Deviation> deviations) {
        // This is a placeholder for procedure deviation checking
        // In a real implementation, this would check if all mandatory procedures were completed
        // based on the CRF data or other procedure tracking mechanisms
        
        if (visit.getMandatoryProcedures() != null && !visit.getMandatoryProcedures().isEmpty()) {
            // Check if CRF data contains evidence of mandatory procedures
            if (visit.getCrfData() == null || visit.getCrfData().isEmpty()) {
                Visit.Deviation procedureDeviation = new Visit.Deviation();
                procedureDeviation.setDeviationId("PROCEDURE_" + System.currentTimeMillis());
                procedureDeviation.setCode("MISSING_CRF_DATA");
                procedureDeviation.setCategory("data");
                procedureDeviation.setSeverity("major");
                procedureDeviation.setDescription("No CRF data provided for visit with mandatory procedures");
                procedureDeviation.setDetectedAt(LocalDateTime.now());
                procedureDeviation.setRequiresReporting(true);
                
                deviations.add(procedureDeviation);
                logger.warn("Procedure deviation detected for visit {}: Missing CRF data", visit.getVisitId());
            }
        }
    }
}
