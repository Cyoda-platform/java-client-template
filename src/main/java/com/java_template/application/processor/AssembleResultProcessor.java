package com.java_template.application.processor;
import com.java_template.application.entity.getuserresult.version_1.GetUserResult;
import com.java_template.application.entity.user.version_1.User;
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

import java.time.Instant;

@Component
public class AssembleResultProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AssembleResultProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public AssembleResultProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing GetUserResult for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(GetUserResult.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(GetUserResult entity) {
        if (entity == null) return false;
        // Basic required fields for any result
        if (entity.getJobReference() == null || entity.getJobReference().isBlank()) return false;
        if (entity.getRetrievedAt() == null || entity.getRetrievedAt().isBlank()) return false;
        if (entity.getStatus() == null || entity.getStatus().isBlank()) return false;

        String status = entity.getStatus().trim().toUpperCase();
        if ("SUCCESS".equals(status)) {
            // success must include a valid user
            User user = entity.getUser();
            if (user == null) return false;
            return user.isValid();
        } else {
            // non-success must include an error message (NOT_FOUND, ERROR, etc.)
            return (entity.getErrorMessage() != null && !entity.getErrorMessage().isBlank());
        }
    }

    private GetUserResult processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<GetUserResult> context) {
        GetUserResult entity = context.entity();

        // Only transition CREATED -> READY when payload is assembled (user or error)
        String currentStatus = entity.getStatus();
        if (currentStatus == null) currentStatus = "";

        // Ensure retrievedAt exists
        if (entity.getRetrievedAt() == null || entity.getRetrievedAt().isBlank()) {
            entity.setRetrievedAt(Instant.now().toString());
        }

        String statusNormalized = entity.getStatus() != null ? entity.getStatus().trim().toUpperCase() : "";

        if ("CREATED".equalsIgnoreCase(statusNormalized)) {
            // Determine if payload is assembled:
            boolean hasSuccessUser = false;
            User user = entity.getUser();
            if (user != null) {
                // If some user fields are missing for validity, we try to minimally enrich retrievedAt/source
                if (user.getRetrievedAt() == null || user.getRetrievedAt().isBlank()) {
                    user.setRetrievedAt(entity.getRetrievedAt());
                }
                if (user.getSource() == null || user.getSource().isBlank()) {
                    user.setSource("ReqRes");
                }
                // We don't modify other user fields; rely on existing data
                try {
                    hasSuccessUser = user.isValid();
                } catch (Exception e) {
                    hasSuccessUser = false;
                }
            }

            boolean hasError = entity.getErrorMessage() != null && !entity.getErrorMessage().isBlank();

            if (hasSuccessUser) {
                entity.setStatus("READY");
                // Clear any stale error message
                entity.setErrorMessage(null);
                logger.info("GetUserResult [{}] assembled with SUCCESS user -> READY", entity.getJobReference());
            } else if (hasError) {
                entity.setStatus("READY");
                logger.info("GetUserResult [{}] assembled with error -> READY", entity.getJobReference());
            } else {
                // Not yet assembled; keep as CREATED. No changes.
                logger.info("GetUserResult [{}] not yet assembled, remains CREATED", entity.getJobReference());
            }
        } else {
            // If already not CREATED, ensure for SUCCESS that user has basic enrichment
            if ("SUCCESS".equalsIgnoreCase(statusNormalized) && entity.getUser() != null) {
                User user = entity.getUser();
                if (user.getRetrievedAt() == null || user.getRetrievedAt().isBlank()) {
                    user.setRetrievedAt(entity.getRetrievedAt());
                }
                if (user.getSource() == null || user.getSource().isBlank()) {
                    user.setSource("ReqRes");
                }
            }
        }

        return entity;
    }
}