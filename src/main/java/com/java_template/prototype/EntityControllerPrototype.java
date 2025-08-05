package com.java_template.prototype;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.*;

@RestController
@RequestMapping(path = "/prototype")
@Slf4j
public class EntityControllerPrototype {

    private final ConcurrentHashMap<String, Mail> mailCache = new ConcurrentHashMap<>();
    private final AtomicLong mailIdCounter = new AtomicLong(1);

    @PostMapping("/mails")
    public ResponseEntity<Map<String, String>> createMail(@RequestBody Mail mail) {
        if (mail == null || !mail.isValid()) {
            log.error("Invalid Mail entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid Mail entity"));
        }
        String technicalId = String.valueOf(mailIdCounter.getAndIncrement());
        mailCache.put(technicalId, mail);
        log.info("Mail entity created with technicalId: {}", technicalId);
        processMail(technicalId, mail);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/mails/{technicalId}")
    public ResponseEntity<?> getMailById(@PathVariable String technicalId) {
        Mail mail = mailCache.get(technicalId);
        if (mail == null) {
            log.error("Mail entity not found for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Mail not found"));
        }
        Map<String, Object> response = new HashMap<>();
        response.put("technicalId", technicalId);
        response.put("isHappy", mail.isHappy());
        response.put("mailList", mail.getMailList());
        response.put("status", mail.getStatus() == null ? "PENDING" : mail.getStatus());
        return ResponseEntity.ok(response);
    }

    // Business logic processing for Mail entity
    private void processMail(String technicalId, Mail mail) {
        log.info("Processing Mail entity with technicalId: {}", technicalId);
        try {
            // Optional criteria check - checkMailIsHappy() simulated here
            boolean criteriaResult = checkMailIsHappy(mail);
            if (criteriaResult) {
                sendHappyMail(mail);
                mail.setStatus("COMPLETED");
                log.info("Happy mail sent for technicalId: {}", technicalId);
            } else {
                sendGloomyMail(mail);
                mail.setStatus("COMPLETED");
                log.info("Gloomy mail sent for technicalId: {}", technicalId);
            }
        } catch (Exception e) {
            mail.setStatus("FAILED");
            log.error("Failed to process mail with technicalId: {}. Error: {}", technicalId, e.getMessage());
        }
    }

    // Criteria check method
    private boolean checkMailIsHappy(Mail mail) {
        // Simple criteria: return the isHappy flag as is
        return mail.isHappy();
    }

    // Processor for sending happy mails
    private void sendHappyMail(Mail mail) {
        // Simulate sending happy mails to all recipients
        for (String recipient : mail.getMailList()) {
            log.info("Sending HAPPY mail to {}", recipient);
            // Implement actual mail sending logic here if needed
        }
    }

    // Processor for sending gloomy mails
    private void sendGloomyMail(Mail mail) {
        // Simulate sending gloomy mails to all recipients
        for (String recipient : mail.getMailList()) {
            log.info("Sending GLOOMY mail to {}", recipient);
            // Implement actual mail sending logic here if needed
        }
    }

}