package com.java_template.entity.prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component("prototype")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);
    private final RestTemplate restTemplate = new RestTemplate();

    // Condition: check if processing is needed (e.g. report not ready)
    public CompletableFuture<ObjectNode> needsProcessing(ObjectNode entity) {
        boolean needsProcessing = !isReportReadyBoolean(entity);
        entity.put("needsProcessing", needsProcessing);
        return CompletableFuture.completedFuture(entity);
    }

    // Condition: check if report is ready
    public CompletableFuture<ObjectNode> isReportReady(ObjectNode entity) {
        boolean ready = isReportReadyBoolean(entity);
        entity.put("isReportReady", ready);
        return CompletableFuture.completedFuture(entity);
    }

    private boolean isReportReadyBoolean(ObjectNode entity) {
        // Simple heuristic: report is ready if totalActivities > 0 and anomalies field is present
        if (!entity.has("totalActivities")) return false;
        int totalActivities = entity.path("totalActivities").asInt(0);
        boolean hasAnomalies = entity.has("anomalies");
        return totalActivities > 0 && hasAnomalies;
    }

    // Action: analyze activities, populate totalActivities, frequentActivityTypes, anomalies
    public CompletableFuture<ObjectNode> analyzeActivities(ObjectNode entity) {
        logger.info("Workflow analyzeActivities started for entity date: {}", entity.path("date").asText());

        JsonNode rawActivities = entity.path("rawActivities");
        int totalActivities = 0;
        Map<String, Integer> activityTypeFrequency = new HashMap<>();
        List<String> anomalies = new ArrayList<>();

        if (rawActivities.isArray()) {
            for (JsonNode activity : rawActivities) {
                totalActivities++;
                String title = activity.path("Title").asText("Unknown");
                activityTypeFrequency.merge(title, 1, Integer::sum);
            }
        } else {
            anomalies.add("No activities data found or not an array");
        }

        if (totalActivities == 0) {
            anomalies.add("No activities found for the date.");
        }
        if (activityTypeFrequency.values().stream().anyMatch(freq -> freq > 100)) {
            anomalies.add("Some activity type frequency unusually high.");
        }

        entity.remove("rawActivities");
        entity.put("totalActivities", totalActivities);
        entity.putPOJO("frequentActivityTypes", new ArrayList<>(activityTypeFrequency.keySet()));
        entity.putPOJO("anomalies", anomalies);

        logger.info("Workflow analyzeActivities finished for date {} with totalActivities {}, anomalies {}",
                entity.path("date").asText(), totalActivities, anomalies);

        return CompletableFuture.completedFuture(entity);
    }

    // Action: send report email if report is ready
    public CompletableFuture<ObjectNode> sendReportEmail(ObjectNode entity) {
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
        if (recipients.isEmpty()) {
            recipients.add("admin@example.com"); // default admin email
        }

        logger.info("sendReportEmail started for date {} to recipients {}", date, recipients);

        try {
            // TODO: replace with actual sending logic
            logger.info("Mock sending report email for date {} to {}", date, recipients);
        } catch (Exception e) {
            logger.error("Error sending report email for date: " + date, e);
        }

        logger.info("sendReportEmail finished for date {}", date);

        return CompletableFuture.completedFuture(entity);
    }
}