package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.application.entity.subscriber.version_1.Subscriber;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
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
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Component
public class SendNotificationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendNotificationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public SendNotificationProcessor(SerializerFactory serializerFactory,
                                     EntityService entityService,
                                     ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Subscriber for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(Subscriber.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Subscriber entity) {
        return entity != null && entity.isValid();
    }

    private Subscriber processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Subscriber> context) {
        Subscriber subscriber = context.entity();

        // Only active subscribers should receive notifications
        if (subscriber.getActive() == null || !subscriber.getActive()) {
            logger.info("Subscriber {} is not active. Skipping notifications.", subscriber.getId());
            return subscriber;
        }

        // Ensure contact is valid
        if (subscriber.getContact() == null || !subscriber.getContact().isValid()) {
            logger.warn("Subscriber {} has invalid contact. Skipping notifications.", subscriber.getId());
            return subscriber;
        }

        // Prepare condition to fetch published laureates
        SearchConditionRequest condition = SearchConditionRequest.group("AND",
            Condition.of("$.status", "EQUALS", "PUBLISHED")
        );

        try {
            CompletableFuture<ArrayNode> laureatesFuture = entityService.getItemsByCondition(
                Laureate.ENTITY_NAME,
                String.valueOf(Laureate.ENTITY_VERSION),
                condition,
                true
            );

            ArrayNode laureateNodes = laureatesFuture.join();
            if (laureateNodes == null || laureateNodes.isEmpty()) {
                logger.info("No published laureates found for notifications.");
                return subscriber;
            }

            // Convert and filter laureates according to subscriber preferences
            List<Laureate> matched = new ArrayList<>();
            for (int i = 0; i < laureateNodes.size(); i++) {
                ObjectNode node = (ObjectNode) laureateNodes.get(i);
                try {
                    Laureate la = objectMapper.treeToValue(node, Laureate.class);
                    if (la == null) continue;

                    // Category match (case-insensitive)
                    boolean categoryMatch = false;
                    if (subscriber.getSubscribedCategories() != null) {
                        for (String cat : subscriber.getSubscribedCategories()) {
                            if (cat != null && la.getCategory() != null &&
                                cat.trim().equalsIgnoreCase(la.getCategory().trim())) {
                                categoryMatch = true;
                                break;
                            }
                        }
                    }

                    if (!categoryMatch) continue;

                    // Year range match
                    boolean yearMatch = true;
                    if (subscriber.getSubscribedYearRange() != null) {
                        String from = subscriber.getSubscribedYearRange().getFrom();
                        String to = subscriber.getSubscribedYearRange().getTo();
                        String laureateYear = la.getYear();
                        if (from != null && to != null && laureateYear != null) {
                            try {
                                int y = Integer.parseInt(laureateYear.trim());
                                int f = Integer.parseInt(from.trim());
                                int t = Integer.parseInt(to.trim());
                                yearMatch = (y >= f && y <= t);
                            } catch (NumberFormatException nfe) {
                                // If parsing fails, fall back to string equality or skip
                                yearMatch = laureateYear.trim().equals(from.trim()) || laureateYear.trim().equals(to.trim());
                            }
                        }
                    }

                    if (yearMatch) {
                        matched.add(la);
                    }
                } catch (Exception ex) {
                    logger.warn("Failed to deserialize laureate node for notification: {}", ex.getMessage());
                }
            }

            if (matched.isEmpty()) {
                logger.info("No matches for subscriber {} found among published laureates.", subscriber.getId());
                return subscriber;
            }

            // "Send" notifications (simulated by logging). In a real implementation this would call an external service.
            String recipient = subscriber.getContact().getEmail();
            List<String> notified = matched.stream()
                .map(l -> l.getFullName() + (l.getYear() != null ? " (" + l.getYear() + ")" : ""))
                .collect(Collectors.toList());

            for (Laureate l : matched) {
                try {
                    // Simulate send per laureate
                    logger.info("Sending notification to {} for laureate {} (category={})",
                        recipient, l.getFullName(), l.getCategory());
                } catch (Exception sendEx) {
                    logger.error("Failed to send notification to {} for laureate {}: {}", recipient, l.getFullName(), sendEx.getMessage());
                    // continue with others
                }
            }

            logger.info("Notified subscriber {} about {} laureate(s).", subscriber.getId(), notified.size());

        } catch (Exception ex) {
            logger.error("Error while fetching laureates or sending notifications for subscriber {}: {}", subscriber.getId(), ex.getMessage());
        }

        // No structural changes to subscriber required; return entity (will be persisted automatically).
        return subscriber;
    }
}