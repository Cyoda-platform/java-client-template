package com.java_template.application.processor;

import com.java_template.application.entity.submission.version_1.Submission;
import com.java_template.common.dto.EntityWithMetadata;
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

import java.time.LocalDateTime;

/**
 * Processor for validating submission data before submission
 */
@Component
public class SubmissionValidationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubmissionValidationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public SubmissionValidationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Submission.class)
                .validate(this::isValidEntityWithMetadata, "Invalid submission entity wrapper")
                .map(this::processSubmissionValidation)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntityWithMetadata(EntityWithMetadata<Submission> entityWithMetadata) {
        Submission entity = entityWithMetadata.entity();
        java.util.UUID technicalId = entityWithMetadata.metadata().getId();
        return entity != null && entity.isValid() && technicalId != null;
    }

    private EntityWithMetadata<Submission> processSubmissionValidation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Submission> context) {

        EntityWithMetadata<Submission> entityWithMetadata = context.entityResponse();
        Submission submission = entityWithMetadata.entity();

        logger.debug("Validating submission: {}", submission.getSubmissionId());

        // Validate required fields
        validateRequiredFields(submission);
        
        // Validate dates
        validateDates(submission);
        
        // Validate study type
        validateStudyType(submission);
        
        // Validate risk category
        validateRiskCategory(submission);
        
        // Update timestamps
        submission.setUpdatedAt(LocalDateTime.now());

        logger.info("Submission {} validation completed successfully", submission.getSubmissionId());
        return entityWithMetadata;
    }

    private void validateRequiredFields(Submission submission) {
        if (submission.getTitle() == null || submission.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Title is required");
        }
        if (submission.getTitle().length() < 5 || submission.getTitle().length() > 200) {
            throw new IllegalArgumentException("Title must be between 5 and 200 characters");
        }
        if (submission.getProtocolId() == null || submission.getProtocolId().trim().isEmpty()) {
            throw new IllegalArgumentException("Protocol ID is required");
        }
        if (submission.getSponsorName() == null || submission.getSponsorName().trim().isEmpty()) {
            throw new IllegalArgumentException("Sponsor name is required");
        }
        if (submission.getPrincipalInvestigator() == null || submission.getPrincipalInvestigator().trim().isEmpty()) {
            throw new IllegalArgumentException("Principal investigator is required");
        }
    }

    private void validateDates(Submission submission) {
        if (submission.getStartDate() != null && submission.getEndDate() != null) {
            if (submission.getEndDate().isBefore(submission.getStartDate())) {
                throw new IllegalArgumentException("End date must be after start date");
            }
        }
    }

    private void validateStudyType(Submission submission) {
        if (submission.getStudyType() != null) {
            String studyType = submission.getStudyType().toLowerCase();
            if (!studyType.equals("clinical_trial") && !studyType.equals("observational") && 
                !studyType.equals("lab_research") && !studyType.equals("other")) {
                throw new IllegalArgumentException("Invalid study type: " + submission.getStudyType());
            }
        }
    }

    private void validateRiskCategory(Submission submission) {
        if (submission.getRiskCategory() != null) {
            String riskCategory = submission.getRiskCategory().toLowerCase();
            if (!riskCategory.equals("low") && !riskCategory.equals("moderate") && !riskCategory.equals("high")) {
                throw new IllegalArgumentException("Invalid risk category: " + submission.getRiskCategory());
            }
        }
    }
}
