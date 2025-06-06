package com.java_template.entity.sendEmailJob;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
@RequiredArgsConstructor
public class sendEmailJobWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(sendEmailJobWorkflow.class);

    private final ObjectMapper objectMapper;

    // Mocked stores, replace with actual in future
    private final java.util.Map<String, JobStatus> analysisJobs = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, Report> analysisReports = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<String, Subscriber> subscribers = new java.util.concurrent.ConcurrentHashMap<>();

    public CompletableFuture<ObjectNode> processSendEmail(ObjectNode entity) {
        // Workflow orchestration only - chain async steps
        return processValidateAnalysisId(entity)
                .thenCompose(this::processCheckJobStatus)
                .thenCompose(this::processPrepareEmailContent)
                .thenCompose(this::processSendEmails)
                .thenApply(this::processMarkEmailSent);
    }

    private CompletableFuture<ObjectNode> processValidateAnalysisId(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            if (entity.get("analysisId") == null || entity.get("analysisId").asText().isEmpty()) {
                logger.warn("Missing analysisId in entity");
                entity.put("workflowStatus", "failed");
                entity.put("workflowMessage", "Missing analysisId");
            } else {
                entity.put("workflowStatus", "validated");
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processCheckJobStatus(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            if (!"validated".equals(entity.get("workflowStatus").asText())) return entity;
            String analysisId = entity.get("analysisId").asText();
            JobStatus jobStatus = analysisJobs.get(analysisId);
            if (jobStatus == null || !"completed".equalsIgnoreCase(jobStatus.getStatus())) {
                logger.warn("Cannot send email, analysis not completed or unknown analysisId={}", analysisId);
                entity.put("workflowStatus", "failed");
                entity.put("workflowMessage", "Analysis job not completed or not found");
            } else {
                entity.put("workflowStatus", "jobVerified");
            }
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processPrepareEmailContent(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            if (!"jobVerified".equals(entity.get("workflowStatus").asText())) return entity;
            String subject = entity.hasNonNull("subject") ? entity.get("subject").asText() : "Report Email";
            String message = entity.hasNonNull("message") ? entity.get("message").asText() : "Here is your requested report.";
            entity.put("subject", subject);
            entity.put("message", message);
            entity.put("workflowStatus", "emailPrepared");
            return entity;
        });
    }

    private CompletableFuture<ObjectNode> processSendEmails(ObjectNode entity) {
        return CompletableFuture.supplyAsync(() -> {
            if (!"emailPrepared".equals(entity.get("workflowStatus").asText())) return entity;
            String analysisId = entity.get("analysisId").asText();
            String subject = entity.get("subject").asText();
            String message = entity.get("message").asText();

            logger.info("Sending email for analysisId={}, subject={}", analysisId, subject);

            // Simulate sending emails to all subscribers
            List<Subscriber> subs = List.copyOf(subscribers.values());
            subs.forEach(s -> logger.info("Sending email to subscriber: {}", s.getEmail()));

            try {
                Thread.sleep(1000); // simulate delay
            } catch (InterruptedException ignored) { }

            logger.info("Emails sent for analysisId={}", analysisId);
            entity.put("workflowStatus", "emailsSent");
            return entity;
        });
    }

    private ObjectNode processMarkEmailSent(ObjectNode entity) {
        if ("emailsSent".equals(entity.get("workflowStatus").asText())) {
            entity.put("entityVersion", ENTITY_VERSION);
            entity.put("emailStatus", "sent");
            entity.put("sentAt", System.currentTimeMillis());
        }
        return entity;
    }

    // Mock classes (replace with your actual classes)
    public static class JobStatus {
        private String status;
        public JobStatus(String status) { this.status = status; }
        public String getStatus() { return status; }
    }

    public static class Report {
    }

    public static class Subscriber {
        private final String email;
        public Subscriber(String email) { this.email = email; }
        public String getEmail() { return email; }
    }
}