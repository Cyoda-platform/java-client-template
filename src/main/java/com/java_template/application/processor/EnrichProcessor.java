package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.time.Instant;

@Component
public class EnrichProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(EnrichProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper mapper = new ObjectMapper();

    public EnrichProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing ImportJob enrichment for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ImportJob.class)
            .validate(this::isValidEntity, "Invalid import job state")
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
        try {
            // Normalize payload to JsonNode for manipulation
            JsonNode payloadNode = mapper.convertValue(job.getPayload(), JsonNode.class);
            String now = Instant.now().toString();

            if (payloadNode == null || payloadNode.isNull()) {
                logger.warn("ImportJob payload is null for job: {}", context.requestId());
                return job;
            }

            if (payloadNode.isArray()) {
                ArrayNode arr = (ArrayNode) payloadNode;
                for (int i = 0; i < arr.size(); i++) {
                    JsonNode item = arr.get(i);
                    if (item != null && item.isObject()) {
                        ((ObjectNode) item).put("importTimestamp", now);
                    }
                }
                // set back normalized payload
                job.setPayload(mapper.convertValue(arr, Object.class));
            } else if (payloadNode.isObject()) {
                ObjectNode obj = (ObjectNode) payloadNode;
                obj.put("importTimestamp", now);
                job.setPayload(mapper.convertValue(obj, Object.class));
            } else {
                // primitive values not expected; leave as-is
                logger.warn("Unexpected payload type for ImportJob {}: {}", context.requestId(), payloadNode.getNodeType());
            }

            // update job status to reflect enrichment stage if not already set
            if (job.getStatus() == null || job.getStatus().isBlank()) {
                job.setStatus("ENRICHED");
            }

        } catch (Exception e) {
            logger.error("Error enriching ImportJob {}: {}", context.requestId(), e.getMessage(), e);
            job.setStatus("FAILED");
            job.setErrorMessage("Enrichment failed: " + e.getMessage());
        }
        return job;
    }
}
