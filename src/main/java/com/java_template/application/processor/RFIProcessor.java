package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.rfi.version_1.RFI;
import com.java_template.application.entity.submission.version_1.Submission;
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
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class RFIProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RFIProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public RFIProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing {} for request: {}", className, request.getId());

        return serializer.withRequest(request)
                .toEntityWithMetadata(Submission.class)
                .validate(this::isValidEntityWithMetadata, "Invalid submission wrapper")
                .map(this::processRFICreation)
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

    private EntityWithMetadata<Submission> processRFICreation(
            ProcessorSerializer.ProcessorEntityResponseExecutionContext<Submission> context) {

        EntityWithMetadata<Submission> entityWithMetadata = context.entityResponse();
        Submission submission = entityWithMetadata.entity();

        logger.debug("Creating RFI for submission: {}", submission.getSubmissionId());

        // Create RFI for missing information
        RFI rfi = createRFIForSubmission(submission);
        
        // Save the RFI
        ModelSpec rfiModelSpec = new ModelSpec()
                .withName(RFI.ENTITY_NAME)
                .withVersion(RFI.ENTITY_VERSION);
        
        EntityWithMetadata<RFI> savedRFI = entityService.save(rfiModelSpec, rfi);
        
        logger.info("RFI {} created for submission {}", rfi.getRfiId(), submission.getSubmissionId());

        submission.setUpdatedAt(LocalDateTime.now());

        return entityWithMetadata;
    }

    private RFI createRFIForSubmission(Submission submission) {
        RFI rfi = new RFI();
        
        rfi.setRfiId("RFI-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        rfi.setParentType("submission");
        rfi.setParentId(submission.getSubmissionId());
        rfi.setTitle("Request for Additional Information");
        rfi.setMessage("Please provide the following additional information or documents to proceed with the review.");
        rfi.setStatus("open");
        rfi.setDueAt(LocalDateTime.now().plusDays(7)); // 7 days to respond
        
        // Set default requested documents
        List<String> requestedDocs = new ArrayList<>();
        requestedDocs.add("protocol_clarification");
        rfi.setRequestedDocuments(requestedDocs);
        
        // Set participants (in real implementation, this would be based on roles)
        List<String> participants = new ArrayList<>();
        participants.add("submitter");
        participants.add("reviewer");
        rfi.setParticipants(participants);
        
        rfi.setCreatedBy("system");
        rfi.setCreatedAt(LocalDateTime.now());
        rfi.setUpdatedAt(LocalDateTime.now());
        
        return rfi;
    }
}
