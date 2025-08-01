package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;
import com.java_template.application.entity.Mail;
import com.java_template.application.entity.HappyMailJob;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, Mail> mailCache = new ConcurrentHashMap<>();
    private final AtomicLong mailIdCounter = new AtomicLong(1);

    private final ConcurrentHashMap<String, HappyMailJob> happyMailJobCache = new ConcurrentHashMap<>();
    private final AtomicLong happyMailJobIdCounter = new AtomicLong(1);

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$",
            Pattern.CASE_INSENSITIVE);

    // POST /prototype/mail - create Mail entity
    @PostMapping("/mail")
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        try {
            if (!mail.isValid()) {
                log.error("Invalid Mail entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            String technicalId = String.valueOf(mailIdCounter.getAndIncrement());
            mailCache.put(technicalId, mail);

            log.info("Mail entity created with technicalId {}", technicalId);

            processMail(technicalId, mail);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error creating Mail entity", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /prototype/mail/{id} - retrieve Mail entity
    @GetMapping("/mail/{id}")
    public ResponseEntity<Mail> getMail(@PathVariable String id) {
        Mail mail = mailCache.get(id);
        if (mail == null) {
            log.error("Mail entity not found with technicalId {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(mail);
    }

    // POST /prototype/happyMailJob - create HappyMailJob entity (usually internal, but exposed)
    @PostMapping("/happyMailJob")
    public ResponseEntity<Map<String, String>> createHappyMailJob(@RequestBody HappyMailJob job) {
        try {
            if (!job.isValid()) {
                log.error("Invalid HappyMailJob entity received");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            String technicalId = String.valueOf(happyMailJobIdCounter.getAndIncrement());
            happyMailJobCache.put(technicalId, job);

            log.info("HappyMailJob entity created with technicalId {}", technicalId);

            processHappyMailJob(technicalId, job);

            Map<String, String> response = new HashMap<>();
            response.put("technicalId", technicalId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error creating HappyMailJob entity", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // GET /prototype/happyMailJob/{id} - retrieve HappyMailJob entity
    @GetMapping("/happyMailJob/{id}")
    public ResponseEntity<HappyMailJob> getHappyMailJob(@PathVariable String id) {
        HappyMailJob job = happyMailJobCache.get(id);
        if (job == null) {
            log.error("HappyMailJob entity not found with technicalId {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(job);
    }

    // Business logic for processing Mail entity
    private void processMail(String technicalId, Mail mail) {
        log.info("Processing Mail entity with technicalId {}", technicalId);

        // Validate mailList not empty and emails valid
        if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
            log.error("Mail list is empty for Mail technicalId {}", technicalId);
            createAndStoreJob(technicalId, "FAILED", "Mail list is empty");
            return;
        }

        for (String email : mail.getMailList()) {
            if (!isValidEmail(email)) {
                log.error("Invalid email '{}' in Mail technicalId {}", email, technicalId);
                createAndStoreJob(technicalId, "FAILED", "Invalid email address: " + email);
                return;
            }
        }

        // Route to appropriate processor based on isHappy flag
        if (mail.getIsHappy() != null && mail.getIsHappy()) {
            sendHappyMail(technicalId, mail);
        } else {
            sendGloomyMail(technicalId, mail);
        }
    }

    private void sendHappyMail(String technicalId, Mail mail) {
        log.info("Sending happy mails for Mail technicalId {}", technicalId);

        // Simulate sending mails with happy content
        boolean success = simulateMailSending(mail.getMailList(), "Happy mail content");

        if (success) {
            createAndStoreJob(technicalId, "COMPLETED", "Happy mails sent successfully");
            log.info("Happy mails sent successfully for Mail technicalId {}", technicalId);
        } else {
            createAndStoreJob(technicalId, "FAILED", "Failed to send happy mails");
            log.error("Failed to send happy mails for Mail technicalId {}", technicalId);
        }
    }

    private void sendGloomyMail(String technicalId, Mail mail) {
        log.info("Sending gloomy mails for Mail technicalId {}", technicalId);

        // Simulate sending mails with gloomy content
        boolean success = simulateMailSending(mail.getMailList(), "Gloomy mail content");

        if (success) {
            createAndStoreJob(technicalId, "COMPLETED", "Gloomy mails sent successfully");
            log.info("Gloomy mails sent successfully for Mail technicalId {}", technicalId);
        } else {
            createAndStoreJob(technicalId, "FAILED", "Failed to send gloomy mails");
            log.error("Failed to send gloomy mails for Mail technicalId {}", technicalId);
        }
    }

    private boolean simulateMailSending(List<String> recipients, String content) {
        // Simulate mail sending; always succeed for prototype
        log.info("Simulating sending mail to recipients: {} with content: {}", recipients, content);
        return true;
    }

    private void createAndStoreJob(String mailTechnicalId, String status, String resultMessage) {
        HappyMailJob job = new HappyMailJob();
        job.setMailTechnicalId(mailTechnicalId);
        job.setStatus(status);
        job.setCreatedAt(LocalDateTime.now());
        job.setResultMessage(resultMessage);

        String jobId = String.valueOf(happyMailJobIdCounter.getAndIncrement());
        happyMailJobCache.put(jobId, job);
        log.info("Created HappyMailJob with id {} for Mail technicalId {} with status {}", jobId, mailTechnicalId, status);

        processHappyMailJob(jobId, job);
    }

    // Business logic for processing HappyMailJob entity
    private void processHappyMailJob(String technicalId, HappyMailJob job) {
        log.info("Processing HappyMailJob entity with technicalId {}", technicalId);

        // For prototype, simply log status and resultMessage
        if ("COMPLETED".equalsIgnoreCase(job.getStatus())) {
            log.info("HappyMailJob {} completed successfully: {}", technicalId, job.getResultMessage());
        } else if ("FAILED".equalsIgnoreCase(job.getStatus())) {
            log.error("HappyMailJob {} failed: {}", technicalId, job.getResultMessage());
        } else {
            log.info("HappyMailJob {} in status: {}", technicalId, job.getStatus());
        }
    }

    private boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }
}