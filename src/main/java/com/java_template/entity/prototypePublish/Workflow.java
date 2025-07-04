package com.java_template.entity.prototypePublish;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.ENTITY_VERSION;

@Component("prototypePublish")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);
    private static final String DEFAULT_ADMIN_EMAIL = "admin@example.com";

    private final RestTemplate restTemplate = new RestTemplate();

    // TODO: Replace with actual injected services
    private final EntityService entityService = null; // placeholder
    private final Object entityModel = null; // placeholder

    public CompletableFuture<ObjectNode> isDatePresent(ObjectNode entity) {
        boolean present = entity.hasNonNull("date") && !entity.path("date").asText().isBlank();
        entity.put("success", present);
        logger.info("Condition isDatePresent: {}", present);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> hasReportForDate(ObjectNode entity) {
        String date = entity.path("date").asText(null);
        boolean hasReport = false;
        if (date != null && !date.isBlank()) {
            try {
                SearchConditionRequest condition = SearchConditionRequest.group("AND",
                        Condition.of("$.date", "EQUALS", date));
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(entityModel, ENTITY_VERSION, condition);
                ArrayNode items = itemsFuture.join();
                hasReport = !items.isEmpty();
            } catch (Exception e) {
                logger.error("Error checking hasReportForDate for date {}: {}", date, e.getMessage());
            }
        }
        entity.put("success", hasReport);
        logger.info("Condition hasReportForDate: {}", hasReport);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> notHasReportForDate(ObjectNode entity) {
        // simply invert hasReportForDate
        return hasReportForDate(entity).thenApply(e -> {
            boolean success = !e.path("success").asBoolean(false);
            e.put("success", success);
            logger.info("Condition notHasReportForDate: {}", success);
            return e;
        });
    }

    public CompletableFuture<ObjectNode> prepareRecipients(ObjectNode entity) {
        List<String> recipients = new ArrayList<>();
        JsonNode recipientsNode = entity.path("recipients");
        if (recipientsNode.isArray()) {
            for (JsonNode r : recipientsNode) {
                if (r.isTextual() && !r.asText().isBlank()) {
                    recipients.add(r.asText());
                }
            }
        }
        if (recipients.isEmpty()) {
            recipients.add(DEFAULT_ADMIN_EMAIL);
        }
        // Store recipients list back into entity as JSON ArrayNode
        entity.set("recipients", entity.arrayNode().addAll(recipients.stream().map(entity::textNode).toList()));
        logger.info("Action prepareRecipients set recipients: {}", recipients);
        return CompletableFuture.completedFuture(entity);
    }

    public CompletableFuture<ObjectNode> fetchReportAndSendEmail(ObjectNode entity) {
        String date = entity.path("date").asText();
        JsonNode recipientsNode = entity.path("recipients");
        List<String> recipients = new ArrayList<>();
        if (recipientsNode.isArray()) {
            for (JsonNode r : recipientsNode) {
                if (r.isTextual() && !r.asText().isBlank()) {
                    recipients.add(r.asText());
                }
            }
        }
        logger.info("Action fetchReportAndSendEmail started for date {} to recipients {}", date, recipients);

        try {
            SearchConditionRequest condition = SearchConditionRequest.group("AND",
                    Condition.of("$.date", "EQUALS", date));
            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(entityModel, ENTITY_VERSION, condition);
            ArrayNode items = itemsFuture.join();

            if (items.isEmpty()) {
                logger.warn("No daily report found for date {} while publishing", date);
            } else {
                JsonNode reportNode = items.get(0);
                sendReportEmail(reportNode, recipients, date);
            }
        } catch (Exception e) {
            logger.error("Error sending report email in fetchReportAndSendEmail for date: " + date, e);
        }

        logger.info("Action fetchReportAndSendEmail finished for date {}", date);
        return CompletableFuture.completedFuture(entity);
    }

    private void sendReportEmail(JsonNode reportNode, List<String> recipients, String date) {
        // TODO: Implement actual email sending logic
        logger.info("Sending report email for date {} to {}", date, recipients);
    }
}