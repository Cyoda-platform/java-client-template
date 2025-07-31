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
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        String technicalId = String.valueOf(mailIdCounter.getAndIncrement());
        mailCache.put(technicalId, mail);
        log.info("Mail entity created with technicalId: {}", technicalId);

        try {
            processMail(technicalId, mail);
            log.info("Processed Mail entity with technicalId: {}", technicalId);
        } catch (Exception e) {
            log.error("Error processing Mail entity with technicalId: {}", technicalId, e);
            // In a production system, consider retry or dead-letter queue here
        }

        Map<String, String> response = new HashMap<>();
        response.put("technicalId", technicalId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/mail/{id}")
    public ResponseEntity<Mail> getMailById(@PathVariable("id") String id) {
        Mail mail = mailCache.get(id);
        if (mail == null) {
            log.error("Mail entity not found for technicalId: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        log.info("Retrieved Mail entity with technicalId: {}", id);
        return ResponseEntity.ok(mail);
    }

    // Business logic processing method for Mail entity
    private void processMail(String technicalId, Mail mail) {
        // Validate mailList is not empty and contains valid emails
        if (mail.getMailList() == null || mail.getMailList().isEmpty()) {
            log.error("MailList is empty for Mail entity with technicalId: {}", technicalId);
            return;
        }
        boolean allValidEmails = mail.getMailList().stream().allMatch(this::isValidEmail);
        if (!allValidEmails) {
            log.error("MailList contains invalid email addresses for Mail entity with technicalId: {}", technicalId);
            return;
        }

        // Depending on isHappy flag, send happy or gloomy mail
        if (Boolean.TRUE.equals(mail.getIsHappy())) {
            sendHappyMail(mail);
            log.info("Sent happy mail for technicalId: {}", technicalId);
        } else {
            sendGloomyMail(mail);
            log.info("Sent gloomy mail for technicalId: {}", technicalId);
        }

        // After sending mails, update mail entity status or result if needed (not persisted here as immutable)
        // This is a prototype, so just log success.
    }

    // Simple email validation method
    private boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) return false;
        // Basic regex for email validation
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    }

    // Simulate sending happy mail
    private void sendHappyMail(Mail mail) {
        // Replace with actual email sending logic using JavaMailSender or similar
        log.info("Simulating sending happy mail to: {}", mail.getMailList());
    }

    // Simulate sending gloomy mail
    private void sendGloomyMail(Mail mail) {
        // Replace with actual email sending logic using JavaMailSender or similar
        log.info("Simulating sending gloomy mail to: {}", mail.getMailList());
    }
}