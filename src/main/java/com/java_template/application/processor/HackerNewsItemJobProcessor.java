package com.java_template.application.processor;

import com.java_template.application.entity.HackerNewsItem;
import com.java_template.application.entity.HackerNewsItemJob;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class HackerNewsItemJobProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final com.java_template.common.service.EntityService entityService;

    public HackerNewsItemJobProcessor(SerializerFactory serializerFactory, com.fasterxml.jackson.databind.ObjectMapper objectMapper, com.java_template.common.service.EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = objectMapper;
        this.entityService = entityService;
        logger.info("HackerNewsItemJobProcessor initialized with SerializerFactory, ObjectMapper, and EntityService");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing HackerNewsItemJob for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(HackerNewsItemJob.class)
                .validate(this::isValidEntity, "Invalid HackerNewsItemJob entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equals(modelSpec.operationName());
    }

    private boolean isValidEntity(HackerNewsItemJob entity) {
        return entity != null && entity.isValid();
    }

    private HackerNewsItemJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<HackerNewsItemJob> context) {
        HackerNewsItemJob job = context.entity();
        String technicalIdStr = job.getTechnicalId();
        UUID technicalId = null;
        try {
            if (technicalIdStr != null) {
                technicalId = UUID.fromString(technicalIdStr);
            }
        } catch (IllegalArgumentException e) {
            logger.error("Invalid technicalId format: {}", technicalIdStr);
            job.setStatus("FAILED");
            return job;
        }

        job.setStatus("PROCESSING");

        try {
            Map<?, ?> hnItemMap = objectMapper.readValue(job.getHnItemJson(), Map.class);

            if (!hnItemMap.containsKey("id") || !(hnItemMap.get("id") instanceof Number)) {
                logger.error("Invalid or missing 'id' field in hnItemJson");
                job.setStatus("FAILED");
                updateJobStatus(technicalId, job);
                return job;
            }

            Long itemId = ((Number) hnItemMap.get("id")).longValue();

            HackerNewsItem item = new HackerNewsItem();
            item.setId(itemId);
            item.setRawJson(job.getHnItemJson());

            Object byObj = hnItemMap.get("by");
            item.setBy(byObj != null ? byObj.toString() : "");

            Object descendantsObj = hnItemMap.get("descendants");
            item.setDescendants(descendantsObj instanceof Number ? ((Number) descendantsObj).intValue() : 0);

            Object kidsObj = hnItemMap.get("kids");
            if (kidsObj instanceof List<?>) {
                List<Long> kidsList = new ArrayList<>();
                for (Object k : (List<?>) kidsObj) {
                    if (k instanceof Number) {
                        kidsList.add(((Number) k).longValue());
                    }
                }
                item.setKids(kidsList);
            } else {
                item.setKids(Collections.emptyList());
            }

            Object scoreObj = hnItemMap.get("score");
            item.setScore(scoreObj instanceof Number ? ((Number) scoreObj).intValue() : 0);

            Object timeObj = hnItemMap.get("time");
            item.setTime(timeObj instanceof Number ? ((Number) timeObj).longValue() : 0L);

            Object titleObj = hnItemMap.get("title");
            item.setTitle(titleObj != null ? titleObj.toString() : "");

            Object typeObj = hnItemMap.get("type");
            item.setType(typeObj != null ? typeObj.toString() : "");

            Object urlObj = hnItemMap.get("url");
            item.setUrl(urlObj != null ? urlObj.toString() : "");

            if (!item.isValid()) {
                logger.error("HackerNewsItem validation failed for item id {}", itemId);
                job.setStatus("FAILED");
                updateJobStatus(technicalId, job);
                return job;
            }

            CompletableFuture<UUID> itemIdFuture = entityService.addItem("HackerNewsItem", com.java_template.common.config.Config.ENTITY_VERSION, item);
            itemIdFuture.get();

            job.setStatus("COMPLETED");
            job.setCompletedAt(System.currentTimeMillis());
            updateJobStatus(technicalId, job);

            logger.info("Successfully processed HackerNewsItemJob technicalId={} and stored item id={}", technicalId, itemId);
        } catch (Exception e) {
            logger.error("Error processing HackerNewsItemJob technicalId={}: {}", technicalId, e.getMessage());
            job.setStatus("FAILED");
            try {
                updateJobStatus(technicalId, job);
            } catch (Exception ex) {
                logger.error("Failed to update job status to FAILED for technicalId={}: {}", technicalId, ex.getMessage());
            }
        }

        return job;
    }

    private void updateJobStatus(UUID technicalId, HackerNewsItemJob job) {
        // No update method in EntityService, so just log the update
        logger.info("Job status update (not persisted): technicalId={}, status={}", technicalId, job.getStatus());
    }

}
