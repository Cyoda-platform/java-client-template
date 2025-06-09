package com.java_template.entity.AnalysisResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class AnalysisResultWorkflow {

    private final ObjectMapper objectMapper;

    // Orchestration method - coordinates workflow steps, no business logic here
    public CompletableFuture<ObjectNode> processAnalysisResult(ObjectNode entity) {
        return processFetchComments(entity)
                .thenCompose(this::processAnalyzeComments)
                .thenCompose(this::processSendReport);
    }

    // Fetch comments from external API and store in entity
    private CompletableFuture<ObjectNode> processFetchComments(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long postId = entity.get("postId").asLong();
                String url = "https://jsonplaceholder.typicode.com/comments?postId=" + postId;
                String response = new org.springframework.web.client.RestTemplate().getForObject(url, String.class);
                JsonNode comments = objectMapper.readTree(response);
                entity.set("comments", comments);
                entity.put("entityVersion", ENTITY_VERSION);
                log.info("Fetched comments for postId: {}", postId);
            } catch (Exception e) {
                log.error("Error fetching comments: {}", e.getMessage());
                // Optionally set error state in entity
                entity.put("error", "fetchCommentsFailed");
            }
            return entity;
        });
    }

    // Analyze comments, compute sentiment and key phrases, update entity state
    private CompletableFuture<ObjectNode> processAnalyzeComments(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonNode comments = entity.get("comments");
                if (comments == null) {
                    log.warn("No comments found in entity for analysis");
                    entity.put("sentimentScore", 0.0);
                    entity.putArray("keyPhrases");
                    return entity;
                }
                double sentimentScore = calculateSentimentScore(comments);
                String[] keyPhrases = extractKeyPhrases(comments);
                entity.put("sentimentScore", sentimentScore);
                entity.putArray("keyPhrases").addAll(objectMapper.valueToTree(keyPhrases));
                entity.put("entityVersion", ENTITY_VERSION);
                log.info("Analyzed comments: sentimentScore={}, keyPhrases={}", sentimentScore, (Object)keyPhrases);
            } catch (Exception e) {
                log.error("Error analyzing comments: {}", e.getMessage());
                entity.put("error", "analysisFailed");
            }
            return entity;
        });
    }

    // Send analysis report via email, update entity state
    private CompletableFuture<ObjectNode> processSendReport(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String email = entity.get("email").asText(null);
                double sentimentScore = entity.get("sentimentScore").asDouble(0.0);
                if (email == null || email.isEmpty()) {
                    log.warn("No email found in entity, skipping send report");
                    entity.put("emailSent", false);
                    return entity;
                }
                boolean emailSent = sendEmail(email, sentimentScore);
                entity.put("emailSent", emailSent);
                entity.put("entityVersion", ENTITY_VERSION);
                log.info("Email sent to {}: {}", email, emailSent);
            } catch (Exception e) {
                log.error("Error sending email report: {}", e.getMessage());
                entity.put("emailSent", false);
                entity.put("error", "emailSendFailed");
            }
            return entity;
        });
    }

    // Placeholder method for sentiment calculation - replace with real logic
    private double calculateSentimentScore(JsonNode comments) {
        // TODO: Implement sentiment analysis
        return 0.75;
    }

    // Placeholder method for key phrase extraction - replace with real logic
    private String[] extractKeyPhrases(JsonNode comments) {
        // TODO: Implement key phrase extraction
        return new String[]{"example"};
    }

    // Placeholder method for sending email - replace with real email sending logic
    private boolean sendEmail(String email, double sentimentScore) {
        // TODO: Implement actual email sending
        log.info("Pretending to send email to {} with sentiment score {}", email, sentimentScore);
        return true;
    }
}