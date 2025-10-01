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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * Processor for validating submissions before they are submitted for review
 * Ensures all required fields and documents are present
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
                .validate(this::isValidEntityWithMetadata, "Invalid submission wrapper")
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
        
        // Validate required documents
        validateRequiredDocuments(submission);
        
        // Update timestamps
        submission.setUpdatedAt(LocalDateTime.now());

        logger.info("Submission {} validation completed successfully", submission.getSubmissionId());

        return entityWithMetadata;
    }

    private void validateRequiredFields(Submission submission) {
        // Validate core required fields
        if (submission.getTitle() == null || submission.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Title is required");
        }
        
        if (submission.getStudyType() == null || submission.getStudyType().trim().isEmpty()) {
            throw new IllegalArgumentException("Study type is required");
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
        
        if (submission.getStartDate() == null) {
            throw new IllegalArgumentException("Start date is required");
        }
        
        if (submission.getEndDate() == null) {
            throw new IllegalArgumentException("End date is required");
        }
        
        if (submission.getStartDate() != null && submission.getEndDate() != null && 
            submission.getStartDate().isAfter(submission.getEndDate())) {
            throw new IllegalArgumentException("Start date must be before end date");
        }
    }

    private void validateRequiredDocuments(Submission submission) {
        // Check if required documents are provided
        if (submission.getAttachmentsRequired() != null && !submission.getAttachmentsRequired().isEmpty()) {
            if (submission.getAttachmentsProvided() == null || submission.getAttachmentsProvided().isEmpty()) {
                throw new IllegalArgumentException("Required documents are missing");
            }
            
            // Check if all required documents are provided
            for (String requiredDoc : submission.getAttachmentsRequired()) {
                if (!submission.getAttachmentsProvided().contains(requiredDoc)) {
                    throw new IllegalArgumentException("Required document missing: " + requiredDoc);
                }
            }
        }
    }
}
