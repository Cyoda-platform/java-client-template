package com.java_template.entity.sendreportentity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Slf4j
@Component("sendreportentity")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Validate if request has non-null date and non-empty adminEmails array
    public CompletableFuture<ObjectNode> isValidRequest(ObjectNode entity) {
        String date = entity.path("date").asText(null);
        JsonNode adminsNode = entity.get("adminEmails");
        boolean valid = date != null && adminsNode != null && adminsNode.isArray() && adminsNode.size() > 0;
        entity.put("isValidRequest", valid);
        return CompletableFuture.completedFuture(entity);
    }

    // Negation of isValidRequest condition
    public CompletableFuture<ObjectNode> notValidRequest(ObjectNode entity) {
        boolean valid = entity.path("isValidRequest").asBoolean(false);
        entity.put("notValidRequest", !valid);
        return CompletableFuture.completedFuture(entity);
    }

    // Fetch activities asynchronously from Fakerest API
    public CompletableFuture<ObjectNode> fetchActivities(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = "https://fakerestapi.azurewebsites.net/api/v1/Activities";
                String response = restTemplate.getForObject(url, String.class);
                JsonNode activitiesNode = objectMapper.readTree(response);
                entity.set("activities", activitiesNode);
                entity.put("fetchAttempted", true);
                logger.info("Fetched activities from Fakerest API");
            } catch (Exception e) {
                logger.error("Failed to fetch activities: {}", e.getMessage(), e);
                entity.put("fetchAttempted", false);
            }
            return entity;
        });
    }

    // Condition: fetch success if activities node exists and is array
    public CompletableFuture<ObjectNode> fetchSuccess(ObjectNode entity) {
        JsonNode activitiesNode = entity.get("activities");
        boolean success = activitiesNode != null && activitiesNode.isArray();
        entity.put("fetchSuccess", success);
        return CompletableFuture.completedFuture(entity);
    }

    // Negation of fetchSuccess
    public CompletableFuture<ObjectNode> notFetchSuccess(ObjectNode entity) {
        boolean success = entity.path("fetchSuccess").asBoolean(false);
        entity.put("notFetchSuccess", !success);
        return CompletableFuture.completedFuture(entity);
    }

    // Analyze activities node, summarize user activities
    public CompletableFuture<ObjectNode> analyzeActivities(ObjectNode entity) {
        JsonNode activitiesNode = entity.get("activities");
        UserActivitySummary summary = new UserActivitySummary();
        if (activitiesNode != null && activitiesNode.isArray()) {
            for (JsonNode activity : activitiesNode) {
                int userId = activity.path("userId").asInt(-1);
                String activityName = activity.path("name").asText("unknown");
                if (userId < 0) {
                    logger.warn("Skipping activity without userId: {}", activity);
                    continue;
                }
                summary.totalActivities++;
                summary.activityTypes.merge(activityName.toLowerCase(), 1, Integer::sum);
            }
        }
        // Flag anomaly if total activities > 10
        if (summary.totalActivities > 10) {
            summary.anomalies.add("Unusually high activity count");
        }
        // Save summary as JSON in entity
        ObjectNode summaryNode = objectMapper.createObjectNode();
        summaryNode.put("totalActivities", summary.totalActivities);
        ObjectNode typesNode = objectMapper.createObjectNode();
        summary.activityTypes.forEach(typesNode::put);
        summaryNode.set("activityTypes", typesNode);
        summaryNode.putArray("anomalies").addAll(summary.anomalies.stream().map(objectMapper::convertValue).toList());
        entity.set("activitySummary", summaryNode);
        entity.put("analysisAttempted", true);
        return CompletableFuture.completedFuture(entity);
    }

    // Condition: analysis success if analysisAttempted true and totalActivities > 0
    public CompletableFuture<ObjectNode> analysisSuccess(ObjectNode entity) {
        boolean attempted = entity.path("analysisAttempted").asBoolean(false);
        int total = entity.path("activitySummary").path("totalActivities").asInt(0);
        boolean success = attempted && total > 0;
        entity.put("analysisSuccess", success);
        return CompletableFuture.completedFuture(entity);
    }

    // Negation of analysisSuccess
    public CompletableFuture<ObjectNode> notAnalysisSuccess(ObjectNode entity) {
        boolean success = entity.path("analysisSuccess").asBoolean(false);
        entity.put("notAnalysisSuccess", !success);
        return CompletableFuture.completedFuture(entity);
    }

    // Generate report node from summary and date
    public CompletableFuture<ObjectNode> generateReportNode(ObjectNode entity) {
        String date = entity.path("date").asText(null);
        JsonNode summaryNode = entity.get("activitySummary");
        if (date == null || summaryNode == null) {
            entity.put("reportGenerated", false);
            return CompletableFuture.completedFuture(entity);
        }
        ObjectNode reportNode = objectMapper.createObjectNode();
        reportNode.put("date", date);
        reportNode.set("summary", summaryNode);
        entity.set("report", reportNode);
        entity.put("reportGenerated", true);
        return CompletableFuture.completedFuture(entity);
    }

    // Condition: report generated is true
    public CompletableFuture<ObjectNode> reportGenerated(ObjectNode entity) {
        boolean generated = entity.path("reportGenerated").asBoolean(false);
        entity.put("reportGenerated", generated);
        return CompletableFuture.completedFuture(entity);
    }

    // Negation of reportGenerated
    public CompletableFuture<ObjectNode> notReportGenerated(ObjectNode entity) {
        boolean generated = entity.path("reportGenerated").asBoolean(false);
        entity.put("notReportGenerated", !generated);
        return CompletableFuture.completedFuture(entity);
    }

    // Process sending report email asynchronously
    public CompletableFuture<ObjectNode> processSendReportEntity(ObjectNode entityNode) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String date = entityNode.path("date").asText(null);
                JsonNode adminsNode = entityNode.get("adminEmails");
                List<String> adminEmails = new ArrayList<>();
                if (adminsNode != null && adminsNode.isArray()) {
                    for (JsonNode emailNode : adminsNode) {
                        adminEmails.add(emailNode.asText());
                    }
                }
                logger.info("Sending report email for date {} to admins: {}", date, String.join(", ", adminEmails));
                entityNode.put("sendAttempted", true);
            } catch (Exception e) {
                logger.error("Error sending report email in workflow: {}", e.getMessage(), e);
                entityNode.put("sendAttempted", false);
            }
            return entityNode;
        });
    }

    // Condition: send success if sendAttempted true
    public CompletableFuture<ObjectNode> sendSuccess(ObjectNode entity) {
        boolean success = entity.path("sendAttempted").asBoolean(false);
        entity.put("sendSuccess", success);
        return CompletableFuture.completedFuture(entity);
    }

    // Negation of sendSuccess
    public CompletableFuture<ObjectNode> notSendSuccess(ObjectNode entity) {
        boolean success = entity.path("sendSuccess").asBoolean(false);
        entity.put("notSendSuccess", !success);
        return CompletableFuture.completedFuture(entity);
    }

    // Helper class for user activity summary, not persisted externally
    private static class UserActivitySummary {
        int totalActivities = 0;
        java.util.Map<String, Integer> activityTypes = new java.util.HashMap<>();
        List<String> anomalies = new ArrayList<>();
    }
}