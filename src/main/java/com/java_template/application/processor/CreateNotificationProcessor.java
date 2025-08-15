package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.dailysummary.version_1.DailySummary;
import com.java_template.application.entity.fetchjob.version_1.FetchJob;
import com.java_template.application.entity.notification.version_1.Notification;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.JsonUtils;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class CreateNotificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(CreateNotificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final JsonUtils jsonUtils;

    public CreateNotificationProcessor(SerializerFactory serializerFactory, EntityService entityService, JsonUtils jsonUtils) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.jsonUtils = jsonUtils;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing CreateNotification for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(FetchJob.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(FetchJob entity) {
        return entity != null && entity.isValid();
    }

    private FetchJob processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<FetchJob> context) {
        FetchJob job = context.entity();
        try {
            // Find the DailySummary for the date
            // Simple condition search
            Object condition = com.java_template.common.util.SearchConditionRequest.group("AND",
                com.java_template.common.util.Condition.of("$.date", "EQUALS", job.getRequestDate())
            );
            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> itemsFuture = entityService.getItemsByCondition(DailySummary.ENTITY_NAME, String.valueOf(DailySummary.ENTITY_VERSION), condition, true);
            com.fasterxml.jackson.databind.node.ArrayNode items = itemsFuture.get();
            if (items == null || items.size() == 0) {
                logger.warn("No DailySummary found for date {} when creating notification", job.getRequestDate());
                return job;
            }
            ObjectNode summaryNode = (ObjectNode) items.get(0);
            DailySummary summary = jsonUtils.fromJsonNode(summaryNode, DailySummary.class);

            Notification notification = new Notification();
            notification.setDate(summary.getDate());
            notification.setSummaryText("Daily NBA scores for " + summary.getDate());
            notification.setRecipientsCount(0); // will be computed by ScheduleNotificationProcessor
            notification.setStatus("PENDING");
            notification.setPayload(summary.getGamesSummary());
            notification.setAttemptCount(0);

            CompletableFuture<UUID> idFuture = entityService.addItem(Notification.ENTITY_NAME, String.valueOf(Notification.ENTITY_VERSION), notification);
            UUID createdId = idFuture.get();
            if (createdId != null) {
                logger.info("Notification created for date {} id {}", summary.getDate(), createdId.toString());
            }
        } catch (Exception e) {
            logger.error("Error in CreateNotificationProcessor: {}", e.getMessage(), e);
        }
        return job;
    }
}
