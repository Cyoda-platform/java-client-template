package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.HackerNewsImportJob;
import com.java_template.application.entity.HackerNewsItem;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class HackerNewsImportJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    private final EntityService entityService;
    private final String className = this.getClass().getSimpleName();

    public HackerNewsImportJobProcessor(SerializerFactory serializerFactory, ObjectMapper objectMapper, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HackerNewsImportJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(HackerNewsImportJob.class)
                .validate(this::isValidEntity, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(HackerNewsImportJob entity) {
        return entity != null && entity.isValid();
    }

    private HackerNewsImportJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HackerNewsImportJob> context) {
        HackerNewsImportJob job = context.entity();
        UUID jobTechnicalId = UUID.fromString(job.getTechnicalId());
        List<Map<String, Object>> items = (List<Map<String, Object>>) context.request().getAdditionalData().get("items");

        boolean allSuccess = true;
        List<HackerNewsItem> itemEntities = new ArrayList<>();

        for (Map<String, Object> itemMap : items) {
            try {
                Object idObj = itemMap.get("id");
                String itemIdStr = idObj != null ? idObj.toString() : null;
                if (itemIdStr == null || itemIdStr.isBlank()) {
                    logger.error("Skipping item with missing id");
                    allSuccess = false;
                    continue;
                }

                HackerNewsItem item = new HackerNewsItem();
                item.setId(Long.parseLong(itemIdStr));
                String typeStr = itemMap.get("type") != null ? itemMap.get("type").toString() : "";
                item.setType(typeStr);
                item.setImportTimestamp(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));

                // Store originalJson string in the item entity
                item.setOriginalJson(objectMapper.writeValueAsString(itemMap));

                processHackerNewsItem(item);

                itemEntities.add(item);

            } catch (Exception e) {
                logger.error("Failed processing item in job {}", jobTechnicalId, e);
                allSuccess = false;
            }
        }

        try {
            if (!itemEntities.isEmpty()) {
                CompletableFuture<List<UUID>> addItemsFuture = entityService.addItems(HackerNewsItem.ENTITY_NAME, "1", itemEntities);
                addItemsFuture.get();
            }
            job.setStatus(allSuccess ? "COMPLETED" : "FAILED");
            CompletableFuture<UUID> updateJobFuture = entityService.addItem(HackerNewsImportJob.ENTITY_NAME, "1", job);
            updateJobFuture.get();
            logger.info("Job {} processing completed with status {}", jobTechnicalId, job.getStatus());
        } catch (Exception e) {
            logger.error("Failed updating job {} status", jobTechnicalId, e);
        }

        return job;
    }

    private void processHackerNewsItem(HackerNewsItem item) {
        boolean hasId = item.getId() != null;
        boolean hasType = item.getType() != null && !item.getType().isBlank();
        if (hasId && hasType) {
            item.setState("VALID");
            logger.info("Item marked VALID");
        } else {
            item.setState("INVALID");
            logger.info("Item marked INVALID");
        }
    }
}
