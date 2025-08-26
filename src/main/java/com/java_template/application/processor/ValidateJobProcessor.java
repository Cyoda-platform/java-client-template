package com.java_template.application.processor;
import com.java_template.application.entity.commentanalysisjob.version_1.CommentAnalysisJob;
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
import java.util.regex.Pattern;

@Component
public class ValidateJobProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateJobProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public ValidateJobProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CommentAnalysisJob for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(CommentAnalysisJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(CommentAnalysisJob entity) {
        return entity != null && entity.isValid();
    }

    private CommentAnalysisJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<CommentAnalysisJob> context) {
        CommentAnalysisJob entity = context.entity();
        
        // Mark as validating while checks occur
        entity.setStatus("VALIDATING");

        String postId = entity.getPostId();
        String recipientEmail = entity.getRecipientEmail();

        // Basic checks: postId must be present and recipientEmail must be a valid-looking email
        boolean postIdValid = postId != null && !postId.isBlank();
        boolean emailValid = recipientEmail != null && !recipientEmail.isBlank() && EMAIL_PATTERN.matcher(recipientEmail).matches();

        if (!postIdValid || !emailValid) {
            logger.warn("Validation failed for CommentAnalysisJob id={} postIdValid={} emailValid={}", entity.getId(), postIdValid, emailValid);
            entity.setStatus("FAILED");
            // mark completion time for failed jobs
            entity.setCompletedAt(Instant.now().toString());
            return entity;
        }

        // Validation passed -> move to ingestion phase
        entity.setStatus("INGESTING");
        return entity;
    }
}