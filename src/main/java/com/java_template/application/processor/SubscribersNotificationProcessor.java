package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Component
public class SubscribersNotificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SubscribersNotificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public SubscribersNotificationProcessor(EntityService entityService, ObjectMapper objectMapper) {
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SubscribersNotification for request: {}", request.getId());

        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.active", "EQUALS", true)
            );

            CompletableFuture<ArrayNode> subscribersFuture = entityService.getItemsByCondition(
                    Subscriber.ENTITY_NAME,
                    String.valueOf(Subscriber.ENTITY_VERSION),
                    condition,
                    true
            );

            List<Subscriber> subscribers = subscribersFuture.thenApply(arrayNode -> {
                List<Subscriber> list = new ArrayList<>();
                for (int i = 0; i < arrayNode.size(); i++) {
                    ObjectNode node = (ObjectNode) arrayNode.get(i);
                    try {
                        Subscriber subscriber = objectMapper.treeToValue(node, Subscriber.class);
                        list.add(subscriber);
                    } catch (Exception e) {
                        logger.error("Failed to deserialize Subscriber entity", e);
                    }
                }
                return list.stream()
                        .filter(Subscriber::isValid)
                        .collect(Collectors.toList());
            }).get();

            // Simulate sending notifications
            for (Subscriber subscriber : subscribers) {
                logger.info("Notifying subscriber: type={}, address={}", subscriber.getContactType(), subscriber.getContactAddress());
                // TODO: Integrate actual notification mechanism here
            }

        } catch (InterruptedException | ExecutionException e) {
            logger.error("Failed to fetch or notify subscribers", e);
            Thread.currentThread().interrupt();
        }

        EntityProcessorCalculationResponse response = new EntityProcessorCalculationResponse();
        response.setId(request.getId());
        response.setSuccess(true);
        return response;
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }
}
