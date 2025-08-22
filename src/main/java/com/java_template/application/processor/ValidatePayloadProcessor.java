package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.importjob.version_1.ImportJob;
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

import java.util.ArrayList;
import java.util.List;

@Component
public class ValidatePayloadProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidatePayloadProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;

    public ValidatePayloadProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
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
        // Basic check: payload must be present to run validation of payload contents
        return entity != null && entity.getPayload() != null && !entity.getPayload().isBlank();
    }

    private ImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportJob> context) {
        ImportJob job = context.entity();
        if (job == null) {
            logger.warn("ImportJob entity is null in validation processor");
            return null;
        }

        String payload = job.getPayload();
        if (payload == null || payload.isBlank()) {
            job.setStatus("FAILED");
            job.setErrorMessage("Payload is missing or empty");
            logger.info("ImportJob {} validation failed: payload missing", context.request().getId());
            return job;
        }

        List<String> missingFields = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(payload);

            // Check for numeric id or string id presence (HN uses numeric id)
            JsonNode idNode = root.get("id");
            if (idNode == null || idNode.isNull()) {
                missingFields.add("id");
            }

            JsonNode typeNode = root.get("type");
            if (typeNode == null || typeNode.isNull() || typeNode.asText().isBlank()) {
                missingFields.add("type");
            }

            if (missingFields.isEmpty()) {
                // Payload contains required fields -> mark job to proceed to enrichment
                job.setStatus("IN_PROGRESS");
                job.setErrorMessage(null);
                logger.info("ImportJob {} validation succeeded, proceeding to enrichment", context.request().getId());
            } else {
                // Missing required fields -> fail the job with details
                job.setStatus("FAILED");
                job.setErrorMessage("Missing required fields: " + String.join(", ", missingFields));
                logger.info("ImportJob {} validation failed: {}", context.request().getId(), job.getErrorMessage());
            }
        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setErrorMessage("Payload JSON parsing error: " + e.getMessage());
            logger.error("ImportJob {} payload parsing failed", context.request().getId(), e);
        }

        return job;
    }
}