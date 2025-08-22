package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.importjob.version_1.ImportJob;
import com.java_template.application.entity.validation_result.version_1.Validation_Result;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

@Component
public class ValidatePayloadProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidatePayloadProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public ValidatePayloadProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ImportJob for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ImportJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ImportJob entity) {
        return entity != null && entity.isValid();
    }

    private ImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportJob> context) {
        ImportJob job = context.entity();

        String payload = job.getPayload();
        Validation_Result result = new Validation_Result();
        List<String> missingFields = new ArrayList<>();

        if (payload == null || payload.isBlank()) {
            // payload absence is already guarded by ImportJob.isValid(), but handle defensively
            result.setIsValid(false);
            result.setErrorMessage("Payload is empty or missing");
            result.setMissingFields(List.of("payload"));
            persistValidationResult(result);
            return job;
        }

        try {
            JsonNode root = objectMapper.readTree(payload);

            // Check presence (and non-null) of "id" and "type"
            if (!root.has("id") || root.get("id").isNull()) {
                missingFields.add("id");
            }
            if (!root.has("type") || root.get("type").isNull()) {
                missingFields.add("type");
            }

            if (missingFields.isEmpty()) {
                result.setIsValid(true);
                result.setMissingFields(null);
                result.setErrorMessage(null);
            } else {
                result.setIsValid(false);
                result.setMissingFields(missingFields);
                result.setErrorMessage("Missing required fields: " + String.join(", ", missingFields));
            }

        } catch (Exception e) {
            logger.warn("Failed to parse payload JSON for ImportJob: {}", e.getMessage());
            result.setIsValid(false);
            result.setErrorMessage("Invalid payload JSON: " + e.getMessage());
        }

        persistValidationResult(result);

        return job;
    }

    private void persistValidationResult(Validation_Result result) {
        try {
            CompletableFuture<UUID> idFuture = entityService.addItem(
                Validation_Result.ENTITY_NAME,
                String.valueOf(Validation_Result.ENTITY_VERSION),
                result
            );
            idFuture.whenComplete((id, ex) -> {
                if (ex != null) {
                    logger.error("Failed to persist Validation_Result: {}", ex.getMessage());
                } else {
                    logger.info("Persisted Validation_Result with technicalId: {}", id);
                }
            });
        } catch (Exception ex) {
            logger.error("Exception while adding Validation_Result: {}", ex.getMessage());
        }
    }
}