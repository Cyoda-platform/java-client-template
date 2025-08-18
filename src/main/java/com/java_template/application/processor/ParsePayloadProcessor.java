package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.application.entity.importjob.version_1.ImportJob;
import com.java_template.application.store.InMemoryDataStore;
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
public class ParsePayloadProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ParsePayloadProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper mapper = new ObjectMapper();

    public ParsePayloadProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Parsing payload for ImportJob request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ImportJob.class)
            .validate(this::isValidEntity, "Invalid job entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(ImportJob entity) {
        return entity != null && entity.getTechnicalId() != null && entity.getPayload() != null;
    }

    private ImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ImportJob> context) {
        ImportJob job = context.entity();
        try {
            JsonNode payload = job.getPayload();
            List<JsonNode> items = new ArrayList<>();
            if (payload.isObject()) {
                items.add(payload);
            } else if (payload.isArray()) {
                ArrayNode arr = (ArrayNode) payload;
                arr.forEach(items::add);
            } else {
                // invalid payload
                logger.error("Invalid payload for job {}", job.getTechnicalId());
                job.setStatus("FAILED");
                job.getProcessedCount();
                return job;
            }
            job.setProcessedCount(items.size());
            InMemoryDataStore.jobsByTechnicalId.put(job.getTechnicalId(), job);
            logger.info("ParsePayloadProcessor: extracted {} items for job {}", items.size(), job.getTechnicalId());
        } catch (Exception e) {
            logger.error("Error parsing payload for job {}: {}", job.getTechnicalId(), e.getMessage(), e);
            job.setStatus("FAILED");
        }
        return job;
    }
}
