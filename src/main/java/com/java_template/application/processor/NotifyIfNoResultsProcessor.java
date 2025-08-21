package com.java_template.application.processor;

import com.java_template.application.entity.searchrequest.version_1.SearchRequest;
import com.java_template.application.entity.notification.version_1.Notification;
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
import java.util.HashMap;
import java.util.Map;

@Component
public class NotifyIfNoResultsProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotifyIfNoResultsProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public NotifyIfNoResultsProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing NotifyIfNoResults for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(SearchRequest.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(SearchRequest entity) {
        return entity != null && "NO_RESULTS".equals(entity.getState());
    }

    private SearchRequest processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<SearchRequest> context) {
        SearchRequest entity = context.entity();
        try {
            if (Boolean.TRUE.equals(entity.getNotifyOnNoResults())) {
                Notification n = new Notification();
                n.setId("notif-" + java.util.UUID.randomUUID().toString());
                n.setSearchRequestId(entity.getTechnicalId());
                n.setUserId(entity.getUserId());
                n.setType(Notification.Type.NO_RESULTS.name());
                Map<String, Object> payload = new HashMap<>();
                payload.put("message", "No pets found for your search");
                n.setPayload(payload);
                n.setCreatedAt(Instant.now().toString());
                n.setDelivered(false);
                n.setDeliveryAttempts(0);

                try {
                    entityService.addItem(Notification.ENTITY_NAME, String.valueOf(Notification.ENTITY_VERSION), n);
                    logger.info("Created Notification {} for SearchRequest {}", n.getId(), entity.getTechnicalId());
                } catch (Exception e) {
                    logger.error("Failed to create notification for SearchRequest {}", entity.getTechnicalId(), e);
                }
            }
            entity.setState("NOTIFIED");
        } catch (Exception e) {
            logger.error("Error during notify If NoResults for SearchRequest {}", entity.getTechnicalId(), e);
        }
        return entity;
    }
}
