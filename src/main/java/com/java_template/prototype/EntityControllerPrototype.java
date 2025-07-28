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
        if (!mail.isValid()) {
            log.error("Invalid Mail entity received");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid mail data"));
        }
        String technicalId = String.valueOf(mailIdCounter.getAndIncrement());
        mailCache.put(technicalId, mail);
        log.info("Mail entity created with technicalId: {}", technicalId);

        processMail(technicalId, mail);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("technicalId", technicalId));
    }

    @GetMapping("/mail/{technicalId}")
    public ResponseEntity<Mail> getMailById(@PathVariable String technicalId) {
        Mail mail = mailCache.get(technicalId);
        if (mail == null) {
            log.error("Mail entity not found for technicalId: {}", technicalId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(mail);
    }

    // Business logic implementation of processMail as per requirements
    private void processMail(String technicalId, Mail mail) {
        log.info("Processing Mail entity with technicalId: {}", technicalId);

        // Validation already done at input, but we can double-check critical fields
        if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
            log.error("Mail list is empty, cannot process mail with technicalId: {}", technicalId);
            return;
        }

        // Criteria check and process accordingly
        if (Boolean.TRUE.equals(mail.getIsHappy())) {
            sendHappyMail(technicalId, mail);
        } else {
            sendGloomyMail(technicalId, mail);
        }
    }

    private void sendHappyMail(String technicalId, Mail mail) {
        try {
            // Simulate sending happy mail to all recipients
            for (String recipient : mail.getMailList()) {
                log.info("Sending HAPPY mail to {}: {}", recipient, mail.getContent());
                // Here would be actual email sending logic via SMTP or JavaMailSender
            }
            log.info("Successfully sent HAPPY mail for technicalId: {}", technicalId);
        } catch (Exception e) {
            log.error("Failed to send HAPPY mail for technicalId: {} due to {}", technicalId, e.getMessage());
        }
    }

    private void sendGloomyMail(String technicalId, Mail mail) {
        try {
            // Simulate sending gloomy mail to all recipients
            for (String recipient : mail.getMailList()) {
                log.info("Sending GLOOMY mail to {}: {}", recipient, mail.getContent());
                // Here would be actual email sending logic via SMTP or JavaMailSender
            }
            log.info("Successfully sent GLOOMY mail for technicalId: {}", technicalId);
        } catch (Exception e) {
            log.error("Failed to send GLOOMY mail for technicalId: {} due to {}", technicalId, e.getMessage());
        }
    }
}