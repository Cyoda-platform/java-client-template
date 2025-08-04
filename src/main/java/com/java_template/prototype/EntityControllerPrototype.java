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
    private final ConcurrentHashMap<String, String> mailStatusCache = new ConcurrentHashMap<>();

    @PostMapping("/mails")
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        if (!mail.isValid()) {
            log.error("Invalid mail entity data");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = String.valueOf(mailIdCounter.getAndIncrement());
        mailCache.put(technicalId, mail);
        mailStatusCache.put(technicalId, "PENDING");
        log.info("Mail entity created with ID: {}", technicalId);

        processMail(technicalId, mail);

        return ResponseEntity.status(HttpStatus.CREATED).body(Collections.singletonMap("technicalId", technicalId));
    }

    @GetMapping("/mails/{technicalId}")
    public ResponseEntity<Map<String, Object>> getMailById(@PathVariable String technicalId) {
        Mail mail = mailCache.get(technicalId);
        if (mail == null) {
            log.error("Mail entity not found for ID: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Map<String, Object> response = new HashMap<>();
        response.put("technicalId", technicalId);
        response.put("isHappy", mail.getIsHappy());
        response.put("mailList", mail.getMailList());
        response.put("content", mail.getContent());
        response.put("status", mailStatusCache.getOrDefault(technicalId, "UNKNOWN"));
        return ResponseEntity.ok(response);
    }

    private void processMail(String technicalId, Mail mail) {
        // Step 1: Validation is already done before saving

        // Step 2: Check Happy Criteria
        boolean isHappy = checkMailHappyCriteria(mail);

        // Step 3: Check Gloomy Criteria (only if not happy)
        if (!isHappy) {
            isHappy = !checkMailGloomyCriteria(mail) ? false : false;
        }

        // Step 4: Set isHappy field in mail entity
        mail.setIsHappy(isHappy);

        // Step 5: Process sending mail based on classification
        try {
            if (isHappy) {
                sendHappyMail(mail);
                mailStatusCache.put(technicalId, "COMPLETED");
                log.info("Sent happy mail for ID: {}", technicalId);
            } else {
                sendGloomyMail(mail);
                mailStatusCache.put(technicalId, "COMPLETED");
                log.info("Sent gloomy mail for ID: {}", technicalId);
            }
        } catch (Exception e) {
            mailStatusCache.put(technicalId, "FAILED");
            log.error("Failed to send mail for ID: {}. Error: {}", technicalId, e.getMessage());
        }
    }

    private boolean checkMailHappyCriteria(Mail mail) {
        // Simple example criteria: content contains positive words
        String content = mail.getContent().toLowerCase();
        List<String> positiveKeywords = Arrays.asList("happy", "joy", "great", "good", "love", "excellent");
        return positiveKeywords.stream().anyMatch(content::contains);
    }

    private boolean checkMailGloomyCriteria(Mail mail) {
        // Simple example criteria: content contains negative words
        String content = mail.getContent().toLowerCase();
        List<String> negativeKeywords = Arrays.asList("sad", "bad", "terrible", "hate", "gloomy", "angry");
        return negativeKeywords.stream().anyMatch(content::contains);
    }

    private void sendHappyMail(Mail mail) {
        // Simulated sending happy mail logic
        for (String recipient : mail.getMailList()) {
            log.info("Sending HAPPY mail to {} with content: {}", recipient, mail.getContent());
        }
    }

    private void sendGloomyMail(Mail mail) {
        // Simulated sending gloomy mail logic
        for (String recipient : mail.getMailList()) {
            log.info("Sending GLOOMY mail to {} with content: {}", recipient, mail.getContent());
        }
    }
}