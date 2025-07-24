package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.Workflow;
import com.java_template.application.entity.NBAGameScore;
import com.java_template.application.entity.EmailNotification;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.context.annotation.Configuration;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, Workflow> workflowCache = new ConcurrentHashMap<>();
    private final AtomicLong workflowIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, NBAGameScore> nbaGameScoreCache = new ConcurrentHashMap<>();
    private final AtomicLong nbaGameScoreIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, EmailNotification> emailNotificationCache = new ConcurrentHashMap<>();
    private final AtomicLong emailNotificationIdCounter = new AtomicLong(1);

    private final RestTemplate restTemplate = new RestTemplate();

    // POST /prototype/workflow - create new Workflow entity (subscribe & request scores)
    @PostMapping("/workflow")
    public ResponseEntity<Map<String, String>> createWorkflow(@RequestBody Workflow workflowRequest) {
        if (workflowRequest == null || workflowRequest.getSubscriberEmail() == null || workflowRequest.getSubscriberEmail().isBlank()) {
            log.error("Invalid subscriber email in workflow creation request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "subscriberEmail is required and cannot be blank"));
        }
        if (workflowRequest.getRequestedDate() == null || workflowRequest.getRequestedDate().isBlank()) {
            log.error("Invalid requestedDate in workflow creation request");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "requestedDate is required and cannot be blank"));
        }
        String id = "workflow-" + workflowIdCounter.getAndIncrement();
        workflowRequest.setStatus("PENDING");
        workflowRequest.setCreatedAt(java.time.Instant.now().toString());
        workflowCache.put(id, workflowRequest);
        log.info("Created Workflow with ID: {}", id);
        processWorkflow(id, workflowRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", id));
    }

    // GET /prototype/workflow/{id} - retrieve Workflow entity by technicalId
    @GetMapping("/workflow/{id}")
    public ResponseEntity<?> getWorkflow(@PathVariable String id) {
        Workflow workflow = workflowCache.get(id);
        if (workflow == null) {
            log.error("Workflow with ID {} not found", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Workflow not found"));
        }
        return ResponseEntity.ok(workflow);
    }

    // GET /prototype/nbagames/{date} - retrieve all NBAGameScore entities for specific date
    @GetMapping("/nbagames/{date}")
    public ResponseEntity<List<NBAGameScore>> getNBAGamesByDate(@PathVariable String date) {
        if (date == null || date.isBlank()) {
            log.error("Invalid date parameter in getNBAGamesByDate");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        List<NBAGameScore> results = new ArrayList<>();
        for (NBAGameScore score : nbaGameScoreCache.values()) {
            if (date.equals(score.getGameDate())) {
                results.add(score);
            }
        }
        return ResponseEntity.ok(results);
    }

    // GET /prototype/emailnotification/{id} - retrieve EmailNotification entity by technicalId
    @GetMapping("/emailnotification/{id}")
    public ResponseEntity<?> getEmailNotification(@PathVariable String id) {
        EmailNotification notification = emailNotificationCache.get(id);
        if (notification == null) {
            log.error("EmailNotification with ID {} not found", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "EmailNotification not found"));
        }
        return ResponseEntity.ok(notification);
    }

    // Process Workflow entity with business logic: fetch NBA scores, save NBAGameScore entities, create and send notifications
    private void processWorkflow(String workflowId, Workflow workflow) {
        log.info("Processing Workflow with ID: {}", workflowId);
        workflow.setStatus("PROCESSING");
        workflowCache.put(workflowId, workflow);

        // Validate email format simple check
        if (!workflow.getSubscriberEmail().contains("@")) {
            log.error("Invalid subscriber email format: {}", workflow.getSubscriberEmail());
            workflow.setStatus("FAILED");
            workflowCache.put(workflowId, workflow);
            return;
        }
        // Validate date format simple check (YYYY-MM-DD)
        if (!workflow.getRequestedDate().matches("\\d{4}-\\d{2}-\\d{2}")) {
            log.error("Invalid requestedDate format: {}", workflow.getRequestedDate());
            workflow.setStatus("FAILED");
            workflowCache.put(workflowId, workflow);
            return;
        }

        // Fetch NBA game scores asynchronously
        try {
            List<NBAGameScore> fetchedScores = fetchNBAScores(workflow.getRequestedDate());
            for (NBAGameScore score : fetchedScores) {
                String scoreId = "nbagamescore-" + nbaGameScoreIdCounter.getAndIncrement();
                nbaGameScoreCache.put(scoreId, score);
                processNBAGameScore(scoreId, score);
            }
            // Create EmailNotification entity
            EmailNotification notification = new EmailNotification();
            notification.setSubscriberEmail(workflow.getSubscriberEmail());
            notification.setNotificationDate(workflow.getRequestedDate());
            notification.setEmailSentStatus("PENDING");
            notification.setSentAt(null);
            String notificationId = "emailnotification-" + emailNotificationIdCounter.getAndIncrement();
            emailNotificationCache.put(notificationId, notification);
            processEmailNotification(notificationId, notification);
            workflow.setStatus("COMPLETED");
            workflowCache.put(workflowId, workflow);
            log.info("Workflow {} processing COMPLETED", workflowId);
        } catch (Exception ex) {
            log.error("Error processing Workflow {}: {}", workflowId, ex.getMessage());
            workflow.setStatus("FAILED");
            workflowCache.put(workflowId, workflow);
        }
    }

    // Fetch NBA scores from external API asynchronously
    private List<NBAGameScore> fetchNBAScores(String date) {
        log.info("Fetching NBA scores for date: {}", date);
        String url = "https://api.sportsdata.io/v3/nba/scores/json/ScoresBasicFinal/" + date + "?key=test";
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<String> entity = new HttpEntity<>(headers);
        NBAGameScore[] response = restTemplate.exchange(url, HttpMethod.GET, entity, NBAGameScore[].class).getBody();
        if (response == null) {
            log.error("No data returned from NBA API for date {}", date);
            return Collections.emptyList();
        }
        log.info("Fetched {} NBA games for date {}", response.length, date);
        return Arrays.asList(response);
    }

    private void processNBAGameScore(String scoreId, NBAGameScore score) {
        log.info("Processing NBAGameScore with ID: {}", scoreId);
        // No extra processing required as per requirements (immutable records)
    }

    private void processEmailNotification(String notificationId, EmailNotification notification) {
        log.info("Processing EmailNotification with ID: {}", notificationId);
        try {
            // Simulated email sending logic
            boolean emailSent = sendEmail(notification.getSubscriberEmail(), notification.getNotificationDate());
            if (emailSent) {
                notification.setEmailSentStatus("SENT");
                notification.setSentAt(java.time.Instant.now().toString());
                log.info("Email sent successfully to {}", notification.getSubscriberEmail());
            } else {
                notification.setEmailSentStatus("FAILED");
                log.error("Failed to send email to {}", notification.getSubscriberEmail());
            }
            emailNotificationCache.put(notificationId, notification);
        } catch (Exception ex) {
            notification.setEmailSentStatus("FAILED");
            emailNotificationCache.put(notificationId, notification);
            log.error("Exception while sending email to {}: {}", notification.getSubscriberEmail(), ex.getMessage());
        }
    }

    // Dummy email sending method
    private boolean sendEmail(String toEmail, String notificationDate) {
        // Simulate email sending logic here, always returns true for prototype
        log.info("Sending email to {} with NBA scores for date {}", toEmail, notificationDate);
        return true;
    }
}