package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;

import com.java_template.application.entity.Mail;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, Mail> mailCache = new ConcurrentHashMap<>();
    private final AtomicLong mailIdCounter = new AtomicLong(1);

    @PostMapping("/mail")
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        // Validate input fields required for creation
        if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
            log.error("Mail creation failed: mailList is empty");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "mailList must not be empty"));
        }
        for (String email : mail.getMailList()) {
            if (email == null || email.isBlank()) {
                log.error("Mail creation failed: mailList contains blank email");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "mailList contains invalid email"));
            }
        }
        if (mail.getContent() == null || mail.getContent().isBlank()) {
            log.error("Mail creation failed: content is blank");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "content must not be blank"));
        }

        // Immutable creation - assign technicalId
        String technicalId = String.valueOf(mailIdCounter.getAndIncrement());
        mail.setStatus("PENDING"); // initial status
        mailCache.put(technicalId, mail);

        log.info("Mail created with technicalId {}", technicalId);

        // Trigger processing event
        processMail(technicalId, mail);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/mail/{technicalId}")
    public ResponseEntity<?> getMailById(@PathVariable String technicalId) {
        Mail mail = mailCache.get(technicalId);
        if (mail == null) {
            log.error("Mail not found with technicalId {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Mail not found"));
        }
        // Return mail with technicalId included
        Map<String, Object> response = new HashMap<>();
        response.put("technicalId", technicalId);
        response.put("isHappy", mail.getIsHappy());
        response.put("mailList", mail.getMailList());
        response.put("content", mail.getContent());
        response.put("status", mail.getStatus());
        return ResponseEntity.ok(response);
    }

    private void processMail(String technicalId, Mail mail) {
        log.info("Processing Mail with ID: {}", technicalId);

        // Step 1: Validation already done on creation

        // Step 2: Criteria Evaluation - simple keyword-based sentiment check (example)
        boolean isHappy = evaluateMailContentForHappiness(mail.getContent());
        mail.setIsHappy(isHappy);

        // Step 3: Processing based on isHappy flag
        try {
            if (isHappy) {
                sendHappyMail(mail);
                mail.setStatus("SENT_HAPPY");
                log.info("Mail {} sent as happy mail", technicalId);
            } else {
                sendGloomyMail(mail);
                mail.setStatus("SENT_GLOOMY");
                log.info("Mail {} sent as gloomy mail", technicalId);
            }
        } catch (Exception e) {
            mail.setStatus("FAILED");
            log.error("Failed to send mail {}: {}", technicalId, e.getMessage());
        }

        // Update cache with new status and isHappy flag
        mailCache.put(technicalId, mail);
    }

    private boolean evaluateMailContentForHappiness(String content) {
        // Basic example: if content contains positive keywords, mail is happy
        String lowerContent = content.toLowerCase();
        List<String> positiveKeywords = Arrays.asList("happy", "wonderful", "great", "joy", "love");
        for (String keyword : positiveKeywords) {
            if (lowerContent.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private void sendHappyMail(Mail mail) {
        // Simulate sending happy mail to all recipients
        for (String recipient : mail.getMailList()) {
            log.info("Sending HAPPY mail to {}", recipient);
            // Real mail sending logic would go here
        }
    }

    private void sendGloomyMail(Mail mail) {
        // Simulate sending gloomy mail to all recipients
        for (String recipient : mail.getMailList()) {
            log.info("Sending GLOOMY mail to {}", recipient);
            // Real mail sending logic would go here
        }
    }
}