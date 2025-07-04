package com.java_template.entity.dailyreportentity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
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
@Component("dailyreportentity")
public class Workflow {

    private static final Logger logger = LoggerFactory.getLogger(Workflow.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityService entityService;
    private final RestTemplate restTemplate = new RestTemplate();

    public Workflow(EntityService entityService) {
        this.entityService = entityService;
    }

    // Initial state action: validate request
    public CompletableFuture<ObjectNode> isValidRequest(ObjectNode entity) {
        try {
            String date = entity.path("date").asText(null);
            JsonNode adminsNode = entity.get("adminEmails");
            boolean valid = date != null && adminsNode != null && adminsNode.isArray() && adminsNode.size() > 0;
            entity.put("isValidRequest", valid);
            logger.info("Validation result for request date {}: {}", date, valid);
        } catch (Exception e) {
            logger.error("Error in isValidRequest: {}", e.getMessage(), e);
            entity.put("isValidRequest", false);
        }
        return CompletableFuture.completedFuture(entity);
    }

    // Condition for validate_request_state transitions
    public CompletableFuture<ObjectNode> isValidRequestCondition(ObjectNode entity) {
        boolean valid = entity.path("isValidRequest").asBoolean(false);
        entity.put("conditionIsValidRequest", valid);
        return CompletableFuture.completedFuture(entity);
    }

    // Fetch activities from Fakerest API
    public CompletableFuture<ObjectNode> fetchActivities(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String date = entity.path("date").asText(null);
                String url = "https://fakerestapi.azurewebsites.net/api/v1/Activities";
                String response = restTemplate.getForObject(url, String.class);
                JsonNode activitiesNode = objectMapper.readTree(response);
                entity.set("activities", activitiesNode);
                logger.info("Fetched activities for date {}", date);
                entity.put("fetchSuccess", true);
            } catch (Exception e) {
                logger.error("Failed to fetch activities: {}", e.getMessage(), e);
                entity.put("fetchSuccess", false);
            }
            return entity;
        });
    }

    // Condition to check fetch success
    public CompletableFuture<ObjectNode> fetchSuccess(ObjectNode entity) {
        boolean success = entity.path("fetchSuccess").asBoolean(false);
        entity.put("conditionFetchSuccess", success);
        return CompletableFuture.completedFuture(entity);
    }

    // Analyze activities
    public CompletableFuture<ObjectNode> analyzeActivities(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonNode activitiesNode = entity.get("activities");
                if (activitiesNode == null || !activitiesNode.isArray()) {
                    entity.put("analysisSuccess", false);
                    logger.error("Activities node missing or invalid");
                    return entity;
                }
                int totalActivities = activitiesNode.size();
                ObjectNode summaryNode = objectMapper.createObjectNode();
                summaryNode.put("totalActivities", totalActivities);

                ObjectNode activityTypes = objectMapper.createObjectNode();
                for (JsonNode activity : activitiesNode) {
                    String name = activity.path("name").asText("unknown").toLowerCase();
                    int count = activityTypes.path(name).asInt(0);
                    activityTypes.put(name, count + 1);
                }
                summaryNode.set("activityTypes", activityTypes);

                ArrayNode anomalies = objectMapper.createArrayNode();
                if (totalActivities == 0) {
                    anomalies.add("No activities recorded");
                }
                if (totalActivities > 10) {
                    anomalies.add("Unusually high activity count");
                }
                summaryNode.set("anomalies", anomalies);

                entity.set("activitySummary", summaryNode);
                entity.put("analysisSuccess", true);
                logger.info("Analysis completed with total activities: {}", totalActivities);
            } catch (Exception e) {
                logger.error("Error analyzing activities: {}", e.getMessage(), e);
                entity.put("analysisSuccess", false);
            }
            return entity;
        });
    }

    // Condition to check analysis success
    public CompletableFuture<ObjectNode> analysisSuccess(ObjectNode entity) {
        boolean success = entity.path("analysisSuccess").asBoolean(false);
        entity.put("conditionAnalysisSuccess", success);
        return CompletableFuture.completedFuture(entity);
    }

    // Generate report node
    public CompletableFuture<ObjectNode> generateReportNode(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String date = entity.path("date").asText(null);
                ObjectNode reportNode = objectMapper.createObjectNode();
                reportNode.put("date", date);
                reportNode.set("activitySummary", entity.get("activitySummary"));
                entity.set("reportNode", reportNode);
                entity.put("reportGenerated", true);
                logger.info("Report generated for date {}", date);
            } catch (Exception e) {
                logger.error("Error generating report node: {}", e.getMessage(), e);
                entity.put("reportGenerated", false);
            }
            return entity;
        });
    }

    // Condition to check report generated
    public CompletableFuture<ObjectNode> reportGenerated(ObjectNode entity) {
        boolean generated = entity.path("reportGenerated").asBoolean(false);
        entity.put("conditionReportGenerated", generated);
        return CompletableFuture.completedFuture(entity);
    }

    // Send report email
    public CompletableFuture<ObjectNode> processSendReportEntity(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ObjectNode reportNode = (ObjectNode) entity.get("reportNode");
                String date = reportNode != null ? reportNode.path("date").asText(null) : null;
                JsonNode adminsNode = entity.get("adminEmails");
                List<String> adminEmails = new ArrayList<>();
                if (adminsNode != null && adminsNode.isArray()) {
                    for (JsonNode emailNode : adminsNode) {
                        adminEmails.add(emailNode.asText());
                    }
                }
                logger.info("Sending report email for date {} to admins: {}", date, String.join(", ", adminEmails));
                // TODO: Implement actual email sending logic here
                entity.put("sendSuccess", true);
            } catch (Exception e) {
                logger.error("Error sending report email in workflow: {}", e.getMessage(), e);
                entity.put("sendSuccess", false);
            }
            return entity;
        });
    }

    // Condition to check send success
    public CompletableFuture<ObjectNode> sendSuccess(ObjectNode entity) {
        boolean success = entity.path("sendSuccess").asBoolean(false);
        entity.put("conditionSendSuccess", success);
        return CompletableFuture.completedFuture(entity);
    }
}