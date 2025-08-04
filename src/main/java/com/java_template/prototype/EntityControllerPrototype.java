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

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    // Cache and ID generator for Mail entity
    private final ConcurrentHashMap<String, Mail> mailCache = new ConcurrentHashMap<>();
    private final AtomicLong mailIdCounter = new AtomicLong(1);

    // Cache and ID generator for HappyMailJob entity
    private final ConcurrentHashMap<String, HappyMailJob> happyMailJobCache = new ConcurrentHashMap<>();
    private final AtomicLong happyMailJobIdCounter = new AtomicLong(1);

    // POST /prototype/mails - Create Mail entity
    @PostMapping("/mails")
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        if (mail == null || mail.getMailList() == null || mail.getMailList().isEmpty()) {
            log.error("Mail creation failed: mailList is null or empty");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        // Generate technicalId
        String technicalId = "mail-" + mailIdCounter.getAndIncrement();
        mailCache.put(technicalId, mail);
        log.info("Mail created with technicalId: {}", technicalId);

        // Process Mail entity
        processMail(technicalId, mail);

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /prototype/mails/{technicalId} - Retrieve Mail entity
    @GetMapping("/mails/{technicalId}")
    public ResponseEntity<Mail> getMail(@PathVariable String technicalId) {
        Mail mail = mailCache.get(technicalId);
        if (mail == null) {
            log.error("Mail not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(mail);
    }

    // POST /prototype/happyMailJobs - Create HappyMailJob entity (optional, usually internal)
    @PostMapping("/happyMailJobs")
    public ResponseEntity<Map<String, String>> createHappyMailJob(@RequestBody HappyMailJob job) {
        if (job == null || job.getMailTechnicalId() == null || job.getMailTechnicalId().isBlank()) {
            log.error("HappyMailJob creation failed: mailTechnicalId is null or blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = "job-" + happyMailJobIdCounter.getAndIncrement();
        job.setStatus("PENDING");
        job.setCreatedAt(LocalDateTime.now());
        happyMailJobCache.put(technicalId, job);
        log.info("HappyMailJob created with technicalId: {}", technicalId);

        // Process HappyMailJob entity
        processHappyMailJob(technicalId, job);

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /prototype/happyMailJobs/{technicalId} - Retrieve HappyMailJob entity
    @GetMapping("/happyMailJobs/{technicalId}")
    public ResponseEntity<HappyMailJob> getHappyMailJob(@PathVariable String technicalId) {
        HappyMailJob job = happyMailJobCache.get(technicalId);
        if (job == null) {
            log.error("HappyMailJob not found with technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(job);
    }

    // Business logic for processing Mail entity according to requirements
    private void processMail(String technicalId, Mail mail) {
        log.info("Processing Mail with technicalId: {}", technicalId);

        // Validate mailList emails format (simple validation)
        boolean validEmails = mail.getMailList().stream()
                .allMatch(email -> email != null && email.contains("@") && !email.isBlank());
        if (!validEmails) {
            log.error("Mail processing failed: invalid email addresses");
            return;
        }

        // Validate subject and content
        if (mail.getSubject() == null || mail.getSubject().isBlank() ||
            mail.getContent() == null || mail.getContent().isBlank()) {
            log.error("Mail processing failed: subject or content is blank");
            return;
        }

        // Determine criteria for happy or gloomy mail
        boolean isHappy = checkMailHappyCriteria(mail) || (!checkMailGloomyCriteria(mail) && Boolean.TRUE.equals(mail.getIsHappy()));
        mail.setIsHappy(isHappy);

        // Log criteria result
        log.info("Mail classified as happy: {}", isHappy);

        // Trigger appropriate processor
        if (isHappy) {
            sendHappyMail(technicalId, mail);
        } else {
            sendGloomyMail(technicalId, mail);
        }

        // Create HappyMailJob entity referencing this mail to track sending status
        HappyMailJob job = new HappyMailJob();
        job.setMailTechnicalId(technicalId);
        job.setStatus("PENDING");
        job.setCreatedAt(LocalDateTime.now());

        String jobId = "job-" + happyMailJobIdCounter.getAndIncrement();
        happyMailJobCache.put(jobId, job);
        log.info("HappyMailJob created with technicalId: {}", jobId);

        processHappyMailJob(jobId, job);
    }

    // Check if mail meets happy criteria (keyword based)
    private boolean checkMailHappyCriteria(Mail mail) {
        String contentLower = mail.getContent().toLowerCase();
        String subjectLower = mail.getSubject().toLowerCase();
        List<String> happyKeywords = Arrays.asList("congratulations", "celebrate", "joy");
        return happyKeywords.stream().anyMatch(keyword -> contentLower.contains(keyword) || subjectLower.contains(keyword));
    }

    // Check if mail meets gloomy criteria (keyword based)
    private boolean checkMailGloomyCriteria(Mail mail) {
        String contentLower = mail.getContent().toLowerCase();
        String subjectLower = mail.getSubject().toLowerCase();
        List<String> gloomyKeywords = Arrays.asList("sorry", "regret", "unfortunate");
        return gloomyKeywords.stream().anyMatch(keyword -> contentLower.contains(keyword) || subjectLower.contains(keyword));
    }

    // Processor for sending happy mail
    private void sendHappyMail(String technicalId, Mail mail) {
        log.info("Sending happy mail with technicalId: {}", technicalId);
        // Simulate sending mail to all recipients
        mail.getMailList().forEach(email -> {
            log.info("Happy mail sent to: {}", email);
        });
    }

    // Processor for sending gloomy mail
    private void sendGloomyMail(String technicalId, Mail mail) {
        log.info("Sending gloomy mail with technicalId: {}", technicalId);
        // Simulate sending mail to all recipients
        mail.getMailList().forEach(email -> {
            log.info("Gloomy mail sent to: {}", email);
        });
    }

    // Process HappyMailJob entity - send mails and update status
    private void processHappyMailJob(String technicalId, HappyMailJob job) {
        log.info("Processing HappyMailJob with technicalId: {}", technicalId);

        Mail mail = mailCache.get(job.getMailTechnicalId());
        if (mail == null) {
            log.error("HappyMailJob processing failed: referenced Mail not found with technicalId: {}", job.getMailTechnicalId());
            job.setStatus("FAILED");
            return;
        }

        try {
            // Here simulate sending mail - in real app integrate with mail API
            List<String> mailList = mail.getMailList();
            if (mailList == null || mailList.isEmpty()) {
                log.error("HappyMailJob processing failed: mailList is empty");
                job.setStatus("FAILED");
                return;
            }
            for (String email : mailList) {
                if (mail.getIsHappy() != null && mail.getIsHappy()) {
                    log.info("Happy mail sent to: {}", email);
                } else {
                    log.info("Gloomy mail sent to: {}", email);
                }
            }
            job.setStatus("SENT");
            log.info("HappyMailJob with technicalId {} completed successfully", technicalId);
        } catch (Exception e) {
            log.error("Error during HappyMailJob processing: {}", e.getMessage());
            job.setStatus("FAILED");
        }
    }
}