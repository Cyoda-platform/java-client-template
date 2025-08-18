package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.java_template.application.entity.hackernewsitem.version_1.HackerNewsItem;
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

import java.time.Instant;
import java.util.UUID;

@Component
public class DispatchItemsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DispatchItemsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public DispatchItemsProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Dispatching items for job request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ImportJob.class)
            .validate(this::isValidEntity, "Invalid job entity for dispatch")
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
            if (payload == null) {
                job.setStatus("FAILED");
                return job;
            }
            if (payload.isObject()) {
                createItemFromPayload(payload, job.getTechnicalId());
            } else if (payload.isArray()) {
                for (JsonNode node : payload) {
                    createItemFromPayload(node, job.getTechnicalId());
                }
            }
            InMemoryDataStore.jobsByTechnicalId.put(job.getTechnicalId(), job);
        } catch (Exception e) {
            logger.error("Error dispatching items for job {}: {}", job.getTechnicalId(), e.getMessage(), e);
            job.setStatus("FAILED");
        }
        return job;
    }

    private void createItemFromPayload(JsonNode payloadItem, String jobTechId) {
        try {
            HackerNewsItem item = new HackerNewsItem();
            item.setTechnicalId("item-" + UUID.randomUUID());
            if (payloadItem.has("id") && payloadItem.get("id").isNumber()) {
                item.setId(payloadItem.get("id").longValue());
            }
            if (payloadItem.has("type") && payloadItem.get("type").isTextual()) {
                item.setType(payloadItem.get("type").asText());
            }
            item.setOriginalJson(payloadItem);
            item.setStatus("PENDING");
            item.setCreatedAt(Instant.now().toString());
            item.setSourceJobTechnicalId(jobTechId);

            InMemoryDataStore.itemsByTechnicalId.put(item.getTechnicalId(), item);
            // Simulate async triggering of ProcessStartProcessor by directly invoking metadata entry
            logger.info("Dispatched item {} for job {}", item.getTechnicalId(), jobTechId);
        } catch (Exception e) {
            logger.error("Error creating dispatched item for job {}: {}", jobTechId, e.getMessage(), e);
        }
    }
}
